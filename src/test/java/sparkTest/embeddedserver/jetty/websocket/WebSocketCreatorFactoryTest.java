package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.websocket.WebSocketCreatorFactory;
import spark.embeddedserver.jetty.websocket.WebSocketCreatorFactory.SparkWebSocketCreator;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerClassWrapper;

import static org.junit.jupiter.api.Assertions.*;


public class WebSocketCreatorFactoryTest {

    @Test
    public void testCreateWebSocketHandler() {
        JettyWebSocketCreator annotated =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(AnnotatedHandler.class));
        assertInstanceOf(SparkWebSocketCreator.class, annotated);
        assertInstanceOf(AnnotatedHandler.class, ((SparkWebSocketCreator) annotated).getHandler());

        JettyWebSocketCreator listener =
                WebSocketCreatorFactory.create(new WebSocketHandlerClassWrapper<>(ListenerHandler.class));
        assertInstanceOf(SparkWebSocketCreator.class, listener);
        assertInstanceOf(ListenerHandler.class, ((SparkWebSocketCreator) listener).getHandler());
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

    public static class ListenerHandler extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            // Called when a new WebSocket connection is opened.
            // You can register message handlers here.
            session.addMessageHandler(String.class, message -> {
                System.out.println("Received message: " + message);
                // Handle the message...
            });
            System.out.println("WebSocket opened: " + session.getId());
        }
    }

    static class InvalidHandler {

    }
}
