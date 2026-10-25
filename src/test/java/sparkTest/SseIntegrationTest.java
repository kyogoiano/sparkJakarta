package sparkTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import spark.Service;
import spark.SseEmitter;
import spark.SseEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseIntegrationTest {

    private final AtomicReference<SseEmitter> emitterRef = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> overflowFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> oversizedFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> customEventLimitFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> customByteLimitFailure = new AtomicReference<>();
    private final List<SseEmitter> idleEmitters = new CopyOnWriteArrayList<>();
    private final CountDownLatch concurrentRouteReady = new CountDownLatch(1);
    private final CountDownLatch concurrentRouteRelease = new CountDownLatch(1);
    private Service service;

    @BeforeEach
    void startService() {
        service = Service.ignite();
        service.ipAddress("127.0.0.1");
        service.port(0);
        service.sse("/events", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send(SseEvent.builder("first line\nsecond line")
                    .event("update")
                    .id("1")
                    .retry(Duration.ofSeconds(1))
                    .build());
            emitter.send("second");
            emitter.complete();
        });
        service.sse("/overflow", (request, response, emitter) -> {
            emitterRef.set(emitter);
            try {
                emitter.send("x".repeat(SseEmitter.MAX_BUFFERED_BYTES));
            } catch (IllegalStateException failure) {
                oversizedFailure.set(failure);
            }
            for (int i = 0; i < SseEmitter.DEFAULT_MAX_BUFFERED_EVENTS; i++) {
                emitter.send("queued");
            }
            try {
                emitter.send("rejected");
            } catch (IllegalStateException failure) {
                overflowFailure.set(failure);
            }
            emitter.complete();
        });
        service.sse("/live", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("initial");
        });
        service.sse("/disconnect", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("initial");
        });
        service.sse("/idle", (request, response, emitter) -> idleEmitters.add(emitter));
        service.sse("/concurrent", 64, 512, (request, response, emitter) -> {
            emitterRef.set(emitter);
            concurrentRouteReady.countDown();
            if (!concurrentRouteRelease.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start the concurrent SSE stream");
            }
        });
        service.sse("/throws", (request, response, emitter) -> {
            emitterRef.set(emitter);
            throw new IllegalArgumentException("failure before SSE response startup");
        });
        service.sse("/large", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("x".repeat(300_000));
            emitter.complete();
        });
        service.sse("/custom-events", 2, 1024, (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("first");
            emitter.send("second");
            try {
                emitter.send("third");
            } catch (IllegalStateException failure) {
                customEventLimitFailure.set(failure);
            }
            emitter.complete();
        });
        service.sse("/custom-bytes", 10, 16, (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("first");
            try {
                emitter.send("second");
            } catch (IllegalStateException failure) {
                customByteLimitFailure.set(failure);
            }
            emitter.complete();
        });
        service.before("/events", (request, response) -> response.header("X-SSE-Before", "ran"));
        service.after("/events", (request, response) -> response.header("X-SSE-After", "ran"));
        service.afterAfter("/events", (request, response) -> response.header("X-SSE-After-After", "ran"));
        service.get("/normal", (request, response) -> "ordinary response");
        service.awaitInitialization();
    }

    @AfterEach
    void stopService() {
        service.stop();
        service.awaitStop();
    }

    @Test
    void streamsFormattedEventsAndCompletesTheAsyncResponse() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/events");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("ran", response.headers().firstValue("X-SSE-Before").orElseThrow());
        assertEquals("ran", response.headers().firstValue("X-SSE-After").orElseThrow());
        assertEquals("ran", response.headers().firstValue("X-SSE-After-After").orElseThrow());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals("event:update", reader.readLine());
            assertEquals("id:1", reader.readLine());
            assertEquals("retry:1000", reader.readLine());
            assertEquals("data:first line", reader.readLine());
            assertEquals("data:second line", reader.readLine());
            assertEquals("", reader.readLine());
            assertEquals("data:second", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertTrue(emitterRef.get().isClosed(), "Completed SSE responses should release the emitter");
    }

    @Test
    void rejectsEventsWhenTheBoundedBufferIsFull() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/overflow");
        assertEquals(200, response.statusCode());
        assertNotNull(overflowFailure.get(), "The producer should be notified when the event queue is full");
        assertNotNull(oversizedFailure.get(), "Oversized individual events should be rejected");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            long lines = reader.lines().count();
            assertEquals(SseEmitter.DEFAULT_MAX_BUFFERED_EVENTS * 2L, lines);
        }
        assertTrue(emitterRef.get().isClosed(), "The completed overflow-test stream should release its emitter");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void sendsEventsAfterTheInitialRouteHasReturned() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/live");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:initial", reader.readLine());
            assertEquals("", reader.readLine());

            emitterRef.get().send("after open");
            emitterRef.get().complete();

            assertEquals("data:after open", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertTrue(emitterRef.get().isClosed());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void closesEmitterAndRejectsSendsAfterClientDisconnects() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/disconnect");
        assertEquals(200, response.statusCode());
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        assertEquals("data:initial", reader.readLine());
        assertEquals("", reader.readLine());
        reader.close();

        SseEmitter emitter = emitterRef.get();
        String payload = "x".repeat(16_000);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!emitter.isClosed() && System.nanoTime() < deadline) {
            try {
                emitter.send(payload);
            } catch (IllegalStateException failure) {
                if (!emitter.isClosed()) {
                    Thread.sleep(10);
                }
            }
            if (!emitter.isClosed()) {
                Thread.sleep(10);
            }
        }

        assertTrue(emitter.isClosed(), "A failed write after disconnect must close the emitter");
        assertThrows(IllegalStateException.class, () -> emitter.send("after disconnect"));
    }

    @Test
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    void sharedHeartbeatSchedulerServesManyIdleConnectionsAndCompletionReleasesThem() throws Exception {
        int connectionCount = 32;
        HttpClient client = HttpClient.newHttpClient();
        List<BufferedReader> readers = new ArrayList<>();

        try {
            for (int i = 0; i < connectionCount; i++) {
                HttpResponse<java.io.InputStream> response = client.send(
                        HttpRequest.newBuilder(URI.create(
                                        "http://127.0.0.1:" + service.port() + "/idle"))
                                .header("Accept", "text/event-stream")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                assertEquals(200, response.statusCode());
                readers.add(new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8)));
            }

            assertEquals(connectionCount, idleEmitters.size());
            for (BufferedReader reader : readers) {
                assertEquals(":", reader.readLine(), "Each idle connection should receive a heartbeat");
                assertEquals("", reader.readLine());
            }

            idleEmitters.forEach(SseEmitter::complete);
            for (BufferedReader reader : readers) {
                assertNull(reader.readLine(), "Completed idle streams should close cleanly");
            }
            assertTrue(idleEmitters.stream().allMatch(SseEmitter::isClosed));
        } finally {
            for (BufferedReader reader : readers) {
                reader.close();
            }
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void concurrentProducersAndCompletionRespectQueueLimitsAndDeliverAcceptedEventsOnce() throws Exception {
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        ExecutorService producerExecutor = Executors.newFixedThreadPool(9);
        Set<String> acceptedEvents = ConcurrentHashMap.newKeySet();
        AtomicLong acceptedBytes = new AtomicLong();
        Future<HttpResponse<java.io.InputStream>> responseFuture = requestExecutor.submit(() -> request("/concurrent"));

        try {
            assertTrue(concurrentRouteReady.await(5, TimeUnit.SECONDS), "SSE route should create its emitter");
            SseEmitter emitter = emitterRef.get();
            CountDownLatch startProducers = new CountDownLatch(1);
            CountDownLatch firstAcceptedEvent = new CountDownLatch(1);
            CompletableFuture<?>[] producers = new CompletableFuture<?>[8];
            for (int producer = 0; producer < producers.length; producer++) {
                int producerId = producer;
                producers[producer] = CompletableFuture.runAsync(() -> {
                    await(startProducers);
                    for (int sequence = 0; sequence < 100; sequence++) {
                        String event = producerId + "-" + sequence + "-" + "x".repeat(24);
                        try {
                            emitter.send(event);
                            acceptedEvents.add(event);
                            acceptedBytes.addAndGet(("data:" + event + "\n\n").getBytes(StandardCharsets.UTF_8).length);
                            firstAcceptedEvent.countDown();
                        } catch (IllegalStateException expectedWhenBoundedBufferOrCompletionWins) {
                            // The pre-start queue is intentionally smaller than the producer workload.
                        }
                    }
                }, producerExecutor);
            }
            CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> {
                await(startProducers);
                await(firstAcceptedEvent);
                emitter.complete();
            }, producerExecutor);

            startProducers.countDown();
            CompletableFuture.allOf(producers).get(5, TimeUnit.SECONDS);
            completion.get(5, TimeUnit.SECONDS);
            assertTrue(acceptedEvents.size() > 0, "Concurrent producers should enqueue some events");
            assertTrue(acceptedEvents.size() <= 64, "The event-count queue limit must hold under contention");
            assertTrue(acceptedBytes.get() <= 512, "The encoded-byte queue limit must hold under contention");
            assertThrows(IllegalStateException.class, () -> emitter.send("after completion"));

            concurrentRouteRelease.countDown();
            HttpResponse<java.io.InputStream> response = responseFuture.get(5, TimeUnit.SECONDS);
            Set<String> receivedEvents = ConcurrentHashMap.newKeySet();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        assertTrue(receivedEvents.add(line.substring("data:".length())),
                                "An accepted event must not be delivered more than once");
                    }
                }
            }
            assertEquals(acceptedEvents, receivedEvents,
                    "Every event accepted before completion should be delivered absent a transport failure");
            assertTrue(emitter.isClosed());
        } finally {
            concurrentRouteRelease.countDown();
            requestExecutor.shutdownNow();
            producerExecutor.shutdownNow();
        }
    }

    @Test
    void routeFailureBeforeStreamingStartsProducesAnOrdinaryErrorResponse() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + "/throws"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertTrue(emitterRef.get().isClosed(), "The emitter should be released when route processing fails");
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
    }

    @Test
    void supportsLargeTextEventsAndOrdinaryHttpOnTheSameServer() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/large");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals(300_005, reader.readLine().length());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }

        HttpResponse<String> ordinaryResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + "/normal"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ordinaryResponse.statusCode());
        assertEquals("ordinary response", ordinaryResponse.body());
    }

    @Test
    void routeCanCustomizeEventAndByteBufferLimits() throws Exception {
        HttpResponse<java.io.InputStream> eventLimitedResponse = request("/custom-events");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(eventLimitedResponse.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:first", reader.readLine());
            assertEquals("", reader.readLine());
            assertEquals("data:second", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertNotNull(customEventLimitFailure.get(), "The configured event-count limit should be enforced");

        HttpResponse<java.io.InputStream> byteLimitedResponse = request("/custom-bytes");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(byteLimitedResponse.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:first", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertNotNull(customByteLimitFailure.get(), "The configured byte limit should be enforced");
    }

    @Test
    void rejectsInvalidCustomBufferLimitsAtRegistration() {
        Service uninitializedService = Service.ignite();
        assertThrows(IllegalArgumentException.class,
                () -> uninitializedService.sse("/invalid-events", 0, 1, (request, response, emitter) -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> uninitializedService.sse("/invalid-bytes", 1, 0, (request, response, emitter) -> { }));
    }

    private HttpResponse<java.io.InputStream> request(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + path))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent SSE test start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }
}
