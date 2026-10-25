/*
 * Copyright 2026
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package spark;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emits text events to one Server-Sent Events client.
 */
public final class SseEmitter {

    public static final int DEFAULT_MAX_BUFFERED_EVENTS = 256;
    public static final int MAX_BUFFERED_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_BUFFERED_BYTES = MAX_BUFFERED_BYTES;
    public static final String CONTENT_TYPE = "text/event-stream;charset=UTF-8";

    private static final byte[] HEARTBEAT = ":\n\n".getBytes(StandardCharsets.UTF_8);
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "spark-sse-heartbeats");
                thread.setDaemon(true);
                return thread;
            });

    static final String REQUEST_ATTRIBUTE = SseEmitter.class.getName();

    private static final Logger LOGGER = LoggerFactory.getLogger(SseEmitter.class);

    private final Object lock = new Object();
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private final int maxBufferedEvents;
    private final int maxBufferedBytes;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private final AtomicBoolean drainInProgress = new AtomicBoolean();
    private final AtomicInteger pendingWriteCallbacks = new AtomicInteger();
    private int bufferedBytes;
    private boolean started;
    private boolean starting;
    private boolean completionRequested;
    private boolean closed;
    private boolean heartbeatPending;
    private AsyncContext asyncContext;
    private ServletOutputStream output;
    private ScheduledFuture<?> heartbeatTask;

    SseEmitter(HttpServletRequest request, HttpServletResponse response,
               int maxBufferedEvents, int maxBufferedBytes) {
        this.request = Objects.requireNonNull(request);
        this.response = Objects.requireNonNull(response);
        if (maxBufferedEvents < 1) {
            throw new IllegalArgumentException("Maximum buffered SSE events must be positive");
        }
        if (maxBufferedBytes < 1) {
            throw new IllegalArgumentException("Maximum buffered SSE bytes must be positive");
        }
        this.maxBufferedEvents = maxBufferedEvents;
        this.maxBufferedBytes = maxBufferedBytes;
    }

    /**
     * Queues a text message as an SSE data event.
     *
     * @throws IllegalStateException if this connection is closed or its bounded buffer is full
     */
    public void send(String data) {
        send(SseEvent.builder(data).build());
    }

    /**
     * Queues an SSE event.
     *
     * @throws IllegalStateException if this connection is closed or its bounded buffer is full
     */
    public void send(SseEvent event) {
        Objects.requireNonNull(event, "SSE event cannot be null");
        if (event.encodedCharacterLength() > maxBufferedBytes) {
            throw new IllegalStateException("SSE event exceeds the maximum buffered size");
        }
        byte[] encoded = event.encode().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBufferedBytes) {
            throw new IllegalStateException("SSE event exceeds the maximum buffered size");
        }

        boolean shouldDrain;
        synchronized (lock) {
            if (closed || completionRequested) {
                throw new IllegalStateException("SSE connection is closed");
            }
            if (queue.size() >= maxBufferedEvents
                    || bufferedBytes + encoded.length > maxBufferedBytes) {
                throw new IllegalStateException("SSE event buffer is full");
            }
            queue.addLast(encoded);
            bufferedBytes += encoded.length;
            shouldDrain = started;
        }
        if (shouldDrain) {
            requestDrain();
        }
    }

    /**
     * Completes the stream after sending all events already queued.
     */
    public void complete() {
        boolean shouldDrain;
        synchronized (lock) {
            if (closed) {
                return;
            }
            completionRequested = true;
            heartbeatPending = false;
            shouldDrain = started;
            cancelHeartbeat();
        }
        if (shouldDrain) {
            requestDrain();
        }
    }

    /**
     * Returns whether the connection has completed or was disconnected.
     */
    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    /**
     * Starts the asynchronous response. Called by Spark after route and filter processing.
     */
    public void start() throws IOException {
        HttpServletRequest currentRequest;
        HttpServletResponse currentResponse;
        synchronized (lock) {
            if (closed || started || starting) {
                return;
            }
            starting = true;
            currentRequest = request;
            currentResponse = response;
        }
        if (!currentRequest.isAsyncSupported()) {
            throw new IllegalStateException(
                    "SSE requires servlet asynchronous support; configure the Spark filter as async-supported");
        }

        currentResponse.setStatus(HttpServletResponse.SC_OK);
        currentResponse.setContentType(CONTENT_TYPE);
        currentResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        currentResponse.setHeader("Cache-Control", "no-cache");
        currentResponse.setHeader("X-Accel-Buffering", "no");

        AsyncContext context = currentRequest.startAsync(currentRequest, currentResponse);
        ServletOutputStream stream;
        try {
            context.setTimeout(0);
            stream = currentResponse.getOutputStream();
        } catch (IOException | RuntimeException failure) {
            synchronized (lock) {
                starting = false;
            }
            context.complete();
            throw failure;
        }
        boolean alreadyClosed;
        synchronized (lock) {
            alreadyClosed = closed;
            asyncContext = context;
            output = stream;
            started = !closed;
            starting = false;
            request = null;
            response = null;
            if (started && !completionRequested) {
                heartbeatTask = HEARTBEAT_EXECUTOR.scheduleAtFixedRate(
                        this::queueHeartbeat, 15, 15, TimeUnit.SECONDS);
            }
        }
        if (alreadyClosed) {
            synchronized (lock) {
                asyncContext = null;
                output = null;
            }
            context.complete();
            return;
        }

        context.addListener(new ConnectionListener());
        stream.setWriteListener(new StreamWriter());
        requestDrain();
    }

    /**
     * Discards pending events and closes the connection. Called by Spark when route processing fails.
     */
    public void abort() {
        AsyncContext context;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            queue.clear();
            bufferedBytes = 0;
            heartbeatPending = false;
            cancelHeartbeat();
            context = asyncContext;
            request = null;
            response = null;
        }
        if (context != null) {
            context.complete();
        }
    }

    private void requestDrain() {
        if (!drainInProgress.compareAndSet(false, true)) {
            return;
        }
        AsyncContext context = asyncContext;
        if (context == null) {
            drainInProgress.set(false);
            return;
        }
        try {
            context.start(this::drainOwned);
        } catch (RuntimeException failure) {
            drainInProgress.set(false);
            closeOnFailure(failure);
        }
    }

    private void drain() {
        if (!drainInProgress.compareAndSet(false, true)) {
            return;
        }
        drainOwned();
    }

    private void drainOwned() {
        try {
            do {
                pendingWriteCallbacks.set(0);
                drainAvailable();
            } while (pendingWriteCallbacks.getAndSet(0) > 0);
        } catch (IOException | RuntimeException failure) {
            closeOnFailure(failure);
        } finally {
            drainInProgress.set(false);
            if (pendingWriteCallbacks.getAndSet(0) > 0) {
                drain();
            } else if (hasPendingOutput()) {
                requestDrain();
            }
        }
    }

    private void drainAvailable() throws IOException {
        ServletOutputStream stream = output;
        while (true) {
            byte[] next;
            boolean shouldComplete = false;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                next = queue.peekFirst();
                if (next == null) {
                    if (completionRequested) {
                        closed = true;
                        heartbeatPending = false;
                        cancelHeartbeat();
                        shouldComplete = true;
                    } else if (heartbeatPending) {
                        next = HEARTBEAT;
                    } else if (stream.isReady()) {
                        stream.flush();
                        return;
                    } else {
                        return;
                    }
                }
                if (next != null && !stream.isReady()) {
                    return;
                }
            }
            if (shouldComplete) {
                asyncContext.complete();
                return;
            }
            stream.write(next);
            synchronized (lock) {
                if (next == HEARTBEAT) {
                    heartbeatPending = false;
                } else if (queue.peekFirst() == next) {
                    queue.removeFirst();
                    bufferedBytes -= next.length;
                }
            }
        }
    }

    private boolean hasPendingOutput() {
        synchronized (lock) {
            return !closed && ((!queue.isEmpty() || heartbeatPending) && output.isReady()
                    || (queue.isEmpty() && completionRequested));
        }
    }

    private void queueHeartbeat() {
        synchronized (lock) {
            if (closed || completionRequested) {
                return;
            }
            heartbeatPending = true;
        }
        requestDrain();
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void closeOnFailure(Throwable failure) {
        AsyncContext context;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            queue.clear();
            bufferedBytes = 0;
            heartbeatPending = false;
            cancelHeartbeat();
            context = asyncContext;
            request = null;
            response = null;
        }
        LOGGER.warn("SSE connection closed after an asynchronous write failure", failure);
        if (context != null) {
            context.complete();
        }
    }

    private final class StreamWriter implements WriteListener {
        @Override
        public void onWritePossible() {
            pendingWriteCallbacks.incrementAndGet();
            drain();
        }

        @Override
        public void onError(Throwable failure) {
            closeOnFailure(failure);
        }
    }

    private final class ConnectionListener implements AsyncListener {
        @Override
        public void onComplete(AsyncEvent event) {
            synchronized (lock) {
                closed = true;
                queue.clear();
                bufferedBytes = 0;
                heartbeatPending = false;
                cancelHeartbeat();
                asyncContext = null;
                output = null;
                request = null;
                response = null;
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            closeOnFailure(new IOException("SSE asynchronous request timed out"));
        }

        @Override
        public void onError(AsyncEvent event) {
            closeOnFailure(event.getThrowable() == null
                    ? new IOException("SSE asynchronous request failed")
                    : event.getThrowable());
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
