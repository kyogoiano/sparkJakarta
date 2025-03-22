package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.*;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.*;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.CoreSession;

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
        return null;
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

