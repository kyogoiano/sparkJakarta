package spark.embeddedserver.jetty.websocket;

import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.websocket.WebSocketCreatorFactory.SparkWebSocketCreator;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketCreatorFactoryTest {

    @Test
    public void testCreateWebSocketHandler() {
        JettyWebSocketCreator annotated =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper(AnnotatedHandler.class));
        assertTrue(annotated instanceof SparkWebSocketCreator);
        assertTrue(((SparkWebSocketCreator) annotated).getHandler() instanceof AnnotatedHandler);

        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper(ListenerHandler.class));
        assertTrue(listener instanceof SparkWebSocketCreator);
        assertTrue(((SparkWebSocketCreator) listener).getHandler() instanceof ListenerHandler);
    }

    @Test
    public void testCannotCreateInvalidHandlers() {
        try {
            WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper(InvalidHandler.class));
            fail("Handler creation should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertEquals(
                    "WebSocket handler must implement 'WebSocketListener' or be annotated as '@WebSocket'",
                    ex.getMessage());
        }
    }

    @Test
    public void testCreate_whenInstantiationException() {
        try {
            WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper(FailingHandler.class));
            fail("Handler creation should have thrown a RunTimeException");
        } catch(RuntimeException ex) {
            assertEquals("Could not instantiate websocket handler", ex.getMessage());
        }

    }

    @WebSocket
    class FailingHandler {

    }

    @WebSocket
    static class AnnotatedHandler {

    }

    static class ListenerHandler extends WebSocketAdapter {

    }

    static class InvalidHandler {

    }
}
