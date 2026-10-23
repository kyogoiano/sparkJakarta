package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaWebSocketIntegrationTest {

    private static volatile CountDownLatch closeLatch = new CountDownLatch(0);
    private static volatile CountDownLatch asyncSendCompletion = new CountDownLatch(0);
    private static volatile AtomicReference<Throwable> asyncSendFailure = new AtomicReference<>();

    private Service service;

    @BeforeEach
    void startServer() {
        closeLatch = new CountDownLatch(4);
        asyncSendCompletion = new CountDownLatch(1);
        asyncSendFailure = new AtomicReference<>();
        service = Service.ignite();
        service.ipAddress("127.0.0.1");
        service.port(0);
        service.webSocketIdleTimeoutMillis(1000L);
        service.webSocketEndpoint(AnnotatedEchoEndpoint.class);
        service.webSocketEndpoint(BinaryEndpoint.class);
        service.webSocketEndpoint(
                ServerEndpointConfig.Builder.create(ProgrammaticEchoEndpoint.class, "/programmatic").build());
        service.init();
        service.awaitInitialization();
    }

    @AfterEach
    void stopServer() {
        service.stop();
        service.awaitStop();
    }

    @Test
    void standardJakartaClientConnectsToAnnotatedAndProgrammaticEndpoints() throws Exception {
        var container = ContainerProvider.getWebSocketContainer();
        JakartaEchoClient annotatedClient = new JakartaEchoClient();
        JakartaEchoClient secondAnnotatedClient = new JakartaEchoClient();
        JakartaEchoClient programmaticClient = new JakartaEchoClient();
        JakartaEchoClient binaryClient = new JakartaEchoClient();
        Session annotatedSession = null;
        Session secondAnnotatedSession = null;
        Session programmaticSession = null;
        Session binarySession = null;

        try {
            annotatedSession = container.connectToServer(
                    annotatedClient, endpointUri("/annotated"));
            annotatedSession.getBasicRemote().sendText("hello");
            String firstAnnotatedResponse = annotatedClient.awaitMessage();
            assertEquals("5000", firstAnnotatedResponse.split(":")[1],
                    "Annotated endpoint should override the global idle timeout");

            secondAnnotatedSession = container.connectToServer(
                    secondAnnotatedClient, endpointUri("/annotated"));
            secondAnnotatedSession.getBasicRemote().sendText("again");
            String secondAnnotatedResponse = secondAnnotatedClient.awaitMessage();
            assertEquals("5000", secondAnnotatedResponse.split(":")[1]);
            assertNotEquals(
                    firstAnnotatedResponse.substring(0, firstAnnotatedResponse.indexOf(':')),
                    secondAnnotatedResponse.substring(0, secondAnnotatedResponse.indexOf(':')),
                    "Jakarta endpoint instances should be created per connection");

            programmaticSession = container.connectToServer(
                    programmaticClient, endpointUri("/programmatic"));
            programmaticSession.getBasicRemote().sendText("hello");
            assertEquals("programmatic:6000:hello", programmaticClient.awaitMessage());
            assertTrue(asyncSendCompletion.await(5, TimeUnit.SECONDS),
                    "Jakarta asynchronous send completion callback should run");
            assertNull(asyncSendFailure.get(), "Jakarta asynchronous send should complete without failure");

            binarySession = container.connectToServer(binaryClient, endpointUri("/binary"));
            binarySession.getBasicRemote().sendBinary(
                    ByteBuffer.wrap("hel".getBytes(StandardCharsets.UTF_8)), false);
            binarySession.getBasicRemote().sendBinary(
                    ByteBuffer.wrap("lo".getBytes(StandardCharsets.UTF_8)), true);
            assertEquals("hello", binaryClient.awaitMessage());
            binarySession.getBasicRemote().sendBinary(ByteBuffer.wrap("world".getBytes(StandardCharsets.UTF_8)));
            assertEquals("world", binaryClient.awaitMessage());
        } finally {
            if (annotatedSession != null && annotatedSession.isOpen()) {
                annotatedSession.close();
            }
            if (secondAnnotatedSession != null && secondAnnotatedSession.isOpen()) {
                secondAnnotatedSession.close();
            }
            if (programmaticSession != null && programmaticSession.isOpen()) {
                programmaticSession.close();
            }
            if (binarySession != null && binarySession.isOpen()) {
                binarySession.close();
            }
        }
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "All Jakarta endpoint close callbacks should run");
    }

    private URI endpointUri(String path) {
        return URI.create("ws://127.0.0.1:" + service.port() + path);
    }

    @ServerEndpoint("/annotated")
    public static class AnnotatedEchoEndpoint {
        private final String instanceId = UUID.randomUUID().toString();
        private long idleTimeoutMillis;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(5000L);
            idleTimeoutMillis = session.getMaxIdleTimeout();
        }

        @OnMessage
        public String echo(String message) {
            return instanceId + ":" + idleTimeoutMillis + ":" + message;
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            closeLatch.countDown();
        }
    }

    @ServerEndpoint("/binary")
    public static class BinaryEndpoint {
        private final ByteArrayOutputStream message = new ByteArrayOutputStream();

        @OnMessage
        public void onBinary(Session session, ByteBuffer fragment, boolean last) {
            byte[] bytes = new byte[fragment.remaining()];
            fragment.get(bytes);
            message.writeBytes(bytes);
            if (last) {
                String completeMessage = new String(message.toByteArray(), StandardCharsets.UTF_8);
                message.reset();
                session.getAsyncRemote().sendText(completeMessage);
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            closeLatch.countDown();
        }
    }

    public static class ProgrammaticEchoEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.setMaxIdleTimeout(6000L);
            session.getAsyncRemote().setSendTimeout(5000L);
            session.addMessageHandler(String.class, (String message) ->
                    session.getAsyncRemote().sendText(
                            "programmatic:" + session.getMaxIdleTimeout() + ":" + message, result -> {
                        if (!result.isOK()) {
                            asyncSendFailure.set(result.getException());
                        }
                        asyncSendCompletion.countDown();
                    }));
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            closeLatch.countDown();
        }
    }

    @ClientEndpoint
    public static class JakartaEchoClient {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void onMessage(String message) {
            messages.add(message);
        }

        String awaitMessage() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "Timed out waiting for a Jakarta WebSocket message");
            return message;
        }
    }
}
