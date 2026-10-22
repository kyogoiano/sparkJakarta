package sparkTest.embeddedserver.jetty.websocket;

import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.websocket.WebSocketCreatorFactory;
import spark.embeddedserver.jetty.websocket.WebSocketCreatorFactory.SparkWebSocketCreator;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerClassWrapper;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerInstanceWrapper;

import static org.junit.jupiter.api.Assertions.*;


public class WebSocketCreatorFactoryTest {

    @Test
    public void testCreateWebSocketHandler() throws Exception {
        JettyWebSocketCreator annotated =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(AnnotatedHandler.class));
        assertInstanceOf(SparkWebSocketCreator.class, annotated);
        assertInstanceOf(AnnotatedHandler.class, ((SparkWebSocketCreator) annotated).getHandler());
        assertSame(((SparkWebSocketCreator) annotated).getHandler(), annotated.createWebSocket(null, null));

        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(ListenerHandler.class));
        assertInstanceOf(SparkWebSocketCreator.class, listener);
        assertInstanceOf(ListenerHandler.class, ((SparkWebSocketCreator) listener).getHandler());
        assertSame(((SparkWebSocketCreator) listener).getHandler(), listener.createWebSocket(null, null));
    }

    @Test
    public void testCreateWebSocketHandlerFromInstanceWrapper() {
        AnnotatedHandler annotatedInstance = new AnnotatedHandler();
        JettyWebSocketCreator annotated =
                WebSocketCreatorFactory.create(new WebSocketHandlerInstanceWrapper(annotatedInstance));
        assertInstanceOf(SparkWebSocketCreator.class, annotated);
        assertSame(annotatedInstance, ((SparkWebSocketCreator) annotated).getHandler());

        ListenerHandler listenerInstance = new ListenerHandler();
        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerInstanceWrapper(listenerInstance));
        assertInstanceOf(SparkWebSocketCreator.class, listener);
        assertSame(listenerInstance, ((SparkWebSocketCreator) listener).getHandler());
    }

    @Test
    public void testCannotCreateInvalidHandlers() {
        try {
            WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(InvalidHandler.class));
            fail("Handler creation should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertEquals(
                    "WebSocket handler must implement 'WebSocketListener' or be annotated as '@WebSocket'",
                    ex.getMessage());
        }

        try {
            new WebSocketHandlerInstanceWrapper(new InvalidHandler());
            fail("Instance wrapper creation should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertEquals(
                    "WebSocket handler must implement 'WebSocketListener' or be annotated as '@WebSocket'",
                    ex.getMessage());
        }
    }

    @Test
    public void testNullValidationInWrappersAndCreator() {
        assertThrows(NullPointerException.class, () -> new WebSocketHandlerClassWrapper<>(null));
        assertThrows(NullPointerException.class, () -> new WebSocketHandlerInstanceWrapper(null));
        assertThrows(NullPointerException.class, () -> new SparkWebSocketCreator(null));
    }

    @Test
    public void testCreate_whenInstantiationException() {
        try {
            WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(FailingHandler.class));
            fail("Handler creation should have thrown a RunTimeException");
        } catch(RuntimeException ex) {
            assertEquals("Could not instantiate websocket handler", ex.getMessage());
        }

    }

    @WebSocket
    static
    class FailingHandler {

    }

    @WebSocket
    protected static class AnnotatedHandler {
        public AnnotatedHandler() {
        }
    }

    public static class ListenerHandler implements Session.Listener {
    }

    static class InvalidHandler {

    }
}
