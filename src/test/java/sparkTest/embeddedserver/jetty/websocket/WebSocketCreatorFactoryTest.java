package sparkTest.embeddedserver.jetty.websocket;

import org.eclipse.jetty.ee11.websocket.server.JettyWebSocketCreator;
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
        Object firstAnnotatedHandler = annotated.createWebSocket(null, null);
        Object secondAnnotatedHandler = annotated.createWebSocket(null, null);
        assertInstanceOf(AnnotatedHandler.class, firstAnnotatedHandler);
        assertInstanceOf(AnnotatedHandler.class, secondAnnotatedHandler);
        assertNotSame(firstAnnotatedHandler, secondAnnotatedHandler);

        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(ListenerHandler.class));
        assertInstanceOf(SparkWebSocketCreator.class, listener);
        Object firstListener = listener.createWebSocket(null, null);
        Object secondListener = listener.createWebSocket(null, null);
        assertInstanceOf(ListenerHandler.class, firstListener);
        assertInstanceOf(ListenerHandler.class, secondListener);
        assertNotSame(firstListener, secondListener);
    }

    @Test
    public void testCreateWebSocketHandlerFromInstanceWrapper() throws Exception {
        AnnotatedHandler annotatedInstance = new AnnotatedHandler();
        JettyWebSocketCreator annotated =
                WebSocketCreatorFactory.create(new WebSocketHandlerInstanceWrapper(annotatedInstance));
        assertInstanceOf(SparkWebSocketCreator.class, annotated);
        assertSame(annotatedInstance, ((SparkWebSocketCreator) annotated).getHandler());
        assertSame(annotatedInstance, annotated.createWebSocket(null, null));
        assertSame(annotatedInstance, annotated.createWebSocket(null, null));

        ListenerHandler listenerInstance = new ListenerHandler();
        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerInstanceWrapper(listenerInstance));
        assertInstanceOf(SparkWebSocketCreator.class, listener);
        assertSame(listenerInstance, ((SparkWebSocketCreator) listener).getHandler());
        assertSame(listenerInstance, listener.createWebSocket(null, null));
        assertSame(listenerInstance, listener.createWebSocket(null, null));
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
        JettyWebSocketCreator creator =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(FailingHandler.class));
        RuntimeException exception = assertThrows(
                RuntimeException.class, () -> creator.createWebSocket(null, null));
        assertEquals("Could not instantiate websocket handler", exception.getMessage());
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
