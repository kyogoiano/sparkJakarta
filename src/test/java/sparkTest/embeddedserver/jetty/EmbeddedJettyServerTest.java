package sparkTest.embeddedserver.jetty;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;
import spark.ExceptionMapper;
import spark.Service;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.EmbeddedJettyServer;
import spark.embeddedserver.jetty.JettyHandler;
import spark.embeddedserver.jetty.JettyServer;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.http.matching.MatcherFilter;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;
import sparkTest.embeddedserver.jetty.websocket.WebSocketTestClient;
import sparkTest.util.SparkTestUtil;

import java.net.ServerSocket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddedJettyServerTest {

    @Test
    public void testIgniteWithPortZeroAndActiveThreadCount() throws Exception {
        Routes routes = Routes.create();
        StaticFilesConfiguration staticFilesConfig = StaticFilesConfiguration.create();
        ExceptionMapper<Exception> exceptionMapper = new ExceptionMapper<>();
        MatcherFilter matcherFilter = new MatcherFilter(routes, staticFilesConfig, exceptionMapper, false, false);
        JettyHandler jettyHandler = new JettyHandler(matcherFilter);

        EmbeddedJettyServer server = new EmbeddedJettyServer(new JettyServer(), jettyHandler);
        assertEquals(0, server.activeThreadCount(), "activeThreadCount should be 0 before ignite");

        try {
            int boundPort = server.ignite("localhost", 0, null, 0, 0, 0);
            assertTrue(boundPort > 0, "Ephemeral port should be resolved to a positive port number");
            assertTrue(server.activeThreadCount() >= 0);

            SparkTestUtil testUtil = new SparkTestUtil(boundPort);
            SparkTestUtil.UrlResponse notFoundResponse = testUtil.doMethod("GET", "/nonexistent", null);
            assertEquals(404, notFoundResponse.status);
            testUtil.closeClient();
        } finally {
            server.extinguish();
        }
    }

    @Test
    public void testIgnitePreservesCustomPreconfiguredConnectors() throws Exception {
        int customPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            customPort = socket.getLocalPort();
        }

        JettyServerFactory customFactory = new JettyServerFactory() {
            @Override
            public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
                Server server = new Server();
                ServerConnector customConnector = new ServerConnector(server);
                customConnector.setHost("localhost");
                customConnector.setPort(customPort);
                server.setConnectors(new Connector[]{customConnector});
                return server;
            }

            @Override
            public Server create(ThreadPool threadPool) {
                return null;
            }
        };

        Routes routes = Routes.create();
        routes.add(spark.route.HttpMethod.get, spark.RouteImpl.create("/custom-connector", (req, res) -> "from-custom-connector"));
        StaticFilesConfiguration staticFilesConfig = StaticFilesConfiguration.create();
        ExceptionMapper<Exception> exceptionMapper = new ExceptionMapper<>();

        EmbeddedJettyServer embeddedServer = (EmbeddedJettyServer) new EmbeddedJettyFactory(customFactory)
            .create(routes, staticFilesConfig, exceptionMapper, false);

        try {
            // Pass a different port (0) to ignite; custom pre-configured connector on customPort must be preserved
            embeddedServer.ignite("localhost", 0, null, 0, 0, 0);

            SparkTestUtil testUtil = new SparkTestUtil(customPort);
            SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/custom-connector", null);
            assertEquals("from-custom-connector", response.body);
            testUtil.closeClient();
        } finally {
            embeddedServer.extinguish();
        }
    }

    @Test
    public void testTrustForwardHeadersTrueVsFalseEndToEnd() throws Exception {
        Service trustedService = Service.ignite().port(0).trustForwardHeaders();
        Service untrustedService = Service.ignite().port(0).untrustForwardHeaders();

        try {
            trustedService.get("/info", (req, res) -> req.ip() + "|" + req.scheme() + "|" + req.host());
            untrustedService.get("/info", (req, res) -> req.ip() + "|" + req.scheme() + "|" + req.host());

            trustedService.awaitInitialization();
            untrustedService.awaitInitialization();

            Map<String, String> forwardedHeaders = Map.of(
                "X-Forwarded-For", "203.0.113.42",
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "proxy.example.org"
            );

            SparkTestUtil trustedClient = new SparkTestUtil(trustedService.port());
            SparkTestUtil.UrlResponse trustedResp = trustedClient.doMethod(
                "GET", "/info", null, false, "text/html", forwardedHeaders
            );
            assertEquals(200, trustedResp.status);
            assertEquals("203.0.113.42|https|proxy.example.org", trustedResp.body);
            trustedClient.closeClient();

            SparkTestUtil untrustedClient = new SparkTestUtil(untrustedService.port());
            SparkTestUtil.UrlResponse untrustedResp = untrustedClient.doMethod(
                "GET", "/info", null, false, "text/html", forwardedHeaders
            );
            assertEquals(200, untrustedResp.status);
            assertTrue(untrustedResp.body.endsWith("|http|localhost:" + untrustedService.port()),
                "Untrusted forward headers should not override scheme or host: " + untrustedResp.body);
            assertFalse(untrustedResp.body.startsWith("203.0.113.42|"),
                "Untrusted forward headers should not override client IP: " + untrustedResp.body);
            untrustedClient.closeClient();
        } finally {
            trustedService.stop();
            untrustedService.stop();
            trustedService.awaitStop();
            untrustedService.awaitStop();
        }
    }

    @Test
    public void testWebSocketSessionListenerAndHttpRoutesCoexistence() throws Exception {
        Service service = Service.ignite().port(0);
        EchoSessionListener wsListener = new EchoSessionListener();

        try {
            service.webSocket("/ws-listener", wsListener);
            service.webSocketIdleTimeoutMillis(5000L);
            service.get("/http-check", (req, res) -> {
                AtomicInteger active = new AtomicInteger(service.activeThreadCount());
                return "http-ok:" + (active.get() > 0);
            });
            service.init();
            service.awaitInitialization();

            int port = service.port();
            SparkTestUtil testUtil = new SparkTestUtil(port);

            // 1. Verify normal HTTP route works alongside WebSocket ContextHandler
            SparkTestUtil.UrlResponse httpResp = testUtil.doMethod("GET", "/http-check", null);
            assertEquals(200, httpResp.status);
            assertEquals("http-ok:true", httpResp.body);

            // 2. Verify unmapped HTTP path still returns 404 (not swallowed by WebSocket context handler)
            SparkTestUtil.UrlResponse notFoundResp = testUtil.doMethod("GET", "/unmapped-route", null);
            assertEquals(404, notFoundResp.status);

            // 3. Verify Jetty 12 Session.Listener WebSocket handler upgrades and exchanges messages
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            WebSocketTestClient clientEndpoint = new WebSocketTestClient();
            container.connectToServer(clientEndpoint, URI.create("ws://localhost:" + port + "/ws-listener"));
            assertTrue(clientEndpoint.awaitClose(5, TimeUnit.SECONDS), "Client should complete WebSocket lifecycle");
            assertTrue(wsListener.messageLatch.await(5, TimeUnit.SECONDS), "WebSocket listener should receive text message");
            assertEquals("Hi Spark!", wsListener.receivedMessage.get());
            assertTrue(wsListener.closeLatch.await(5, TimeUnit.SECONDS), "WebSocket listener should receive close event");

            testUtil.closeClient();
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    @Test
    public void testVirtualThreadsSupportAndLifecycle() throws Exception {
        Service service = Service.ignite();
        service.port(0);
        service.useVirtualThreads();

        AtomicReference<Boolean> beforeIsVirtual = new AtomicReference<>(false);
        AtomicReference<Boolean> afterIsVirtual = new AtomicReference<>(false);
        AtomicReference<Boolean> afterAfterIsVirtual = new AtomicReference<>(false);
        AtomicReference<Boolean> exceptionIsVirtual = new AtomicReference<>(false);
        CountDownLatch afterAfterLatch = new CountDownLatch(1);

        try {
            service.before("/virtual-thread-check", (req, res) -> beforeIsVirtual.set(Thread.currentThread().isVirtual()));
            service.get("/virtual-thread-check", (req, res) -> String.valueOf(Thread.currentThread().isVirtual()));
            service.after("/virtual-thread-check", (req, res) -> afterIsVirtual.set(Thread.currentThread().isVirtual()));
            service.afterAfter("/virtual-thread-check", (req, res) -> {
                afterAfterIsVirtual.set(Thread.currentThread().isVirtual());
                afterAfterLatch.countDown();
            });

            service.get("/virtual-thread-error", (req, res) -> {
                throw new IllegalArgumentException("boom");
            });
            service.exception(IllegalArgumentException.class, (ex, req, res) -> {
                exceptionIsVirtual.set(Thread.currentThread().isVirtual());
                res.status(422);
                res.body("handled-on-vt:" + Thread.currentThread().isVirtual());
            });

            service.awaitInitialization();

            // Calling useVirtualThreads after initialization must throw IllegalStateException
            assertThrows(IllegalStateException.class, () -> service.useVirtualThreads(false));

            int port = service.port();
            SparkTestUtil testUtil = new SparkTestUtil(port);

            SparkTestUtil.UrlResponse resp = testUtil.doMethod("GET", "/virtual-thread-check", null);
            assertEquals(200, resp.status);
            assertEquals("true", resp.body, "Route should be handled on a JDK 21+ Virtual Thread");
            assertTrue(beforeIsVirtual.get(), "Before filter should run on a Virtual Thread");
            assertTrue(afterIsVirtual.get(), "After filter should run on a Virtual Thread");
            assertTrue(afterAfterLatch.await(5, TimeUnit.SECONDS));
            assertTrue(afterAfterIsVirtual.get(), "AfterAfter filter should run on a Virtual Thread");

            SparkTestUtil.UrlResponse errResp = testUtil.doMethod("GET", "/virtual-thread-error", null);
            assertEquals(422, errResp.status);
            assertEquals("handled-on-vt:true", errResp.body);
            assertTrue(exceptionIsVirtual.get(), "Exception handler should run on a Virtual Thread");

            testUtil.closeClient();
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    @Test
    public void testPlatformThreadsWhenVirtualThreadsDisabled() throws Exception {
        Service service = Service.ignite();
        service.port(0);
        service.useVirtualThreads(false);

        try {
            service.get("/platform-thread-check", (req, res) -> String.valueOf(Thread.currentThread().isVirtual()));
            service.awaitInitialization();

            int port = service.port();
            SparkTestUtil testUtil = new SparkTestUtil(port);

            SparkTestUtil.UrlResponse resp = testUtil.doMethod("GET", "/platform-thread-check", null);
            assertEquals(200, resp.status);
            assertEquals("false", resp.body, "Request should be handled on a platform thread when virtual threads are disabled");

            testUtil.closeClient();
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    public static class EchoSessionListener implements Session.Listener {
        final CountDownLatch messageLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();
        private Session session;

        @Override
        public void onWebSocketOpen(Session session) {
            this.session = session;
            session.demand();
        }

        @Override
        public void onWebSocketText(String message) {
            receivedMessage.set(message);
            messageLatch.countDown();
            if (session != null && session.isOpen()) {
                session.sendText("echo:" + message, Callback.NOOP);
                session.demand();
            }
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason, Callback callback) {
            closeLatch.countDown();
            callback.succeed();
        }
    }
}
