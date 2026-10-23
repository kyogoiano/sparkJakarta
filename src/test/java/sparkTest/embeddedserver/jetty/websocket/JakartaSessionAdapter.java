package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.*;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;

public class JakartaSessionAdapter implements Session {

    private final CoreSession coreSession;
    private final String id;

    public JakartaSessionAdapter(CoreSession coreSession) {
        this.coreSession = coreSession;
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public WebSocketContainer getContainer() {
        return null;
    }

    @Override
    public void addMessageHandler(MessageHandler messageHandler) throws IllegalStateException {

    }

    @Override
    public <T> void addMessageHandler(Class<T> aClass, MessageHandler.Whole<T> whole) {

    }

    @Override
    public <T> void addMessageHandler(Class<T> aClass, MessageHandler.Partial<T> partial) {

    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return Set.of();
    }

    @Override
    public void removeMessageHandler(MessageHandler messageHandler) {

    }

    @Override
    public String getProtocolVersion() {
        return "";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return "";
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return List.of();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return coreSession.isInputOpen() && coreSession.isOutputOpen();
    }

    @Override
    public long getMaxIdleTimeout() {
        return 0;
    }

    @Override
    public void setMaxIdleTimeout(long l) {

    }

    @Override
    public void setMaxBinaryMessageBufferSize(int i) {

    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return 0;
    }

    @Override
    public void setMaxTextMessageBufferSize(int i) {

    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return 0;
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        return new RemoteEndpoint.Async() {
            @Override
            public long getSendTimeout() {
                return 0;
            }

            @Override
            public void setSendTimeout(long timeoutmillis) {
            }

            @Override
            public void sendText(String text, SendHandler handler) {
                coreSession.sendFrame(
                    new Frame(OpCode.TEXT, text),
                    Callback.from(() -> handler.onResult(new SendResult()), t -> handler.onResult(new SendResult(t))),
                    false
                );
            }

            @Override
            public Future<Void> sendText(String text) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                coreSession.sendFrame(
                    new Frame(OpCode.TEXT, text),
                    Callback.from(() -> future.complete(null), future::completeExceptionally),
                    false
                );
                return future;
            }

            @Override
            public Future<Void> sendBinary(java.nio.ByteBuffer data) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                coreSession.sendFrame(
                    new Frame(OpCode.BINARY, data),
                    Callback.from(() -> future.complete(null), future::completeExceptionally),
                    false
                );
                return future;
            }

            @Override
            public void sendBinary(java.nio.ByteBuffer data, SendHandler handler) {
                coreSession.sendFrame(
                    new Frame(OpCode.BINARY, data),
                    Callback.from(() -> handler.onResult(new SendResult()), t -> handler.onResult(new SendResult(t))),
                    false
                );
            }

            @Override
            public Future<Void> sendObject(Object data) {
                return sendText(String.valueOf(data));
            }

            @Override
            public void sendObject(Object data, SendHandler handler) {
                sendText(String.valueOf(data), handler);
            }

            @Override
            public void setBatchingAllowed(boolean allowed) {
            }

            @Override
            public boolean getBatchingAllowed() {
                return false;
            }

            @Override
            public void flushBatch() {
                coreSession.flush(Callback.NOOP);
            }

            @Override
            public void sendPing(java.nio.ByteBuffer applicationData) {
                coreSession.sendFrame(
                    new Frame(OpCode.PING, applicationData),
                    Callback.NOOP,
                    false
                );
            }

            @Override
            public void sendPong(java.nio.ByteBuffer applicationData) {
                coreSession.sendFrame(
                    new Frame(OpCode.PONG, applicationData),
                    Callback.NOOP,
                    false
                );
            }
        };
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        return null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void close() throws IOException {
        coreSession.close(Callback.NOOP);
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        int code = closeReason.getCloseCode().getCode();
        String reason = closeReason.getReasonPhrase();
        // Use a no-op callback to perform a synchronous close.
        coreSession.close(code, reason, Callback.NOOP);
    }

    @Override
    public URI getRequestURI() {
        return null;
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return Map.of();
    }

    @Override
    public String getQueryString() {
        return "";
    }

    @Override
    public Map<String, String> getPathParameters() {
        return Map.of();
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return Map.of();
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public Set<Session> getOpenSessions() {
        return Set.of();
    }
}

