package sparkTest;


import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.junit.jupiter.api.*;
import spark.ExceptionMapper;
import spark.HaltException;
import spark.Service;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static spark.Service.ignite;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceTest {

    private static final String IP_ADDRESS = "127.0.0.1";
    private static final int NOT_FOUND_STATUS_CODE = HttpServletResponse.SC_NOT_FOUND;

    private static String errorMessage = "";

    private Service service;
    private final EmbeddedJettyFactory embeddedJettyFactory = new EmbeddedJettyFactory(new JettyServerFactory() {
        @Override
        public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
            return new Server();
        }

        @Override
        public Server create(ThreadPool threadPool) {
            return null;
        }
    });

    @BeforeEach
    public void test() {
        service = ignite();
    }

    @AfterEach
    public void tearDown() {
        service.stop();
        service.awaitStop();
    }


    @Test
    public void testEmbeddedServerIdentifier_defaultAndSet() {
        assertEquals(
            EmbeddedServers.defaultIdentifier(),
            service.embeddedServerIdentifier(), "Should return defaultIdentifier()");

        Object obj = new Object();

        service.embeddedServerIdentifier(obj);

        assertEquals(
            obj,
            service.embeddedServerIdentifier(), "Should return expected obj");
    }

    @Test
    public void testEmbeddedServerIdentifier_thenThrowIllegalStateException() {
        Object obj = new Object();
        service.init();
        assertThrows(IllegalStateException.class, () -> service.embeddedServerIdentifier(obj));
    }

    @Test
    public void testHalt_whenOutParameters_thenThrowHaltException() {
        assertThrows(HaltException.class, service::halt);
    }

    @Test
    public void testHalt_whenStatusCode_thenThrowHaltException() {
        assertThrows(HaltException.class, () ->service.halt(NOT_FOUND_STATUS_CODE));
    }

    @Test
    public void testHalt_whenBodyContent_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt("error"));
    }

    @Test
    public void testHalt_whenStatusCodeAndBodyContent_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt(NOT_FOUND_STATUS_CODE, "error"));
    }

    @Test
    // junit halts with this test
    public void testIpAddress_whenInitializedTrue_thenThrowIllegalStateException() {
        //"This must be done before route mapping has begun");

        service.initExceptionHandler((e) -> errorMessage = "Custom init error");
        service.init();

        assertThrows(IllegalStateException.class, () -> service.ipAddress(IP_ADDRESS));
    }

    @Test
    public void testPort_whenInitializedFalse() {

        service.port(8080);
        service.init(); //initialize after port configuration

        int port = service.port();

        assertEquals(8080, port, "Port should be set to the Port that was specified");
    }

    @Test
    public void testPort_whenInitializedTrue_thenThrowIllegalStateException() {
        //"This must be done before route mapping has begun");
        service.init();

        assertThrows(IllegalStateException.class, () -> service.port(8080));
    }

    @Test
    public void testGetPort_whenInitializedFalse_thenThrowIllegalStateException() {
        //"This must be done after route mapping has begun");
        assertThrows(IllegalStateException.class, service::port);
    }


    @Test
    public void testGetPort_whenInitializedTrue_Default() {
        int expectedPort = Service.SPARK_DEFAULT_PORT;
        service.init();

        int actualPort = service.port();

        assertEquals(expectedPort, actualPort, "Port retrieved should be the port setted");
    }


    @Test
    public void testThreadPool_whenMaxMinAndTimeoutParameters_thenThrowIllegalStateException() {
        service.init();
        assertThrows(IllegalStateException.class, () ->
            service.threadPool(100, 50, 75));
    }

    @Test
    public void testSecure() {
        assertDoesNotThrow( ()  ->
            service.secure(new URI("keyfile"), "keypassword",  new URI("truststorefile"), "truststorepassword"));

    }

    @Test
    public void testSecure_whenInitializedTrue_thenThrowIllegalStateException() {
       //"This must be done before route mapping has begun");
        service.init();
        assertThrows(IllegalStateException.class, () ->
            service.secure(null, null, null, null));
    }

    @Test
    public void testSecure_whenInitializedFalse_thenThrowIllegalArgumentException() {
        //"Must provide a keystore file to run secured");
        assertThrows(IllegalArgumentException.class, () ->
            service.secure(null, null, null, null));
    }

    @Test
    public void testWebSocketIdleTimeoutMillis_whenInitializedTrue_thenThrowIllegalStateException() {
        //"This must be done before route mapping has begun");
        service.init();
        assertThrows(IllegalStateException.class, () -> service.webSocketIdleTimeoutMillis(100));
    }

    @Test
    public void testWebSocket_whenInitializedTrue_thenThrowIllegalStateException() {
        //"This must be done before route mapping has begun");
        service.init();
        assertThrows(IllegalStateException.class, () ->
            service.webSocket("/", DummyWebSocketListener.class));
    }

    @Test
    public void testWebSocket_whenPathNull_thenThrowNullPointerException() {
        //"WebSocket path cannot be null");
        assertThrows(NullPointerException.class, () ->
            service.webSocket(null, new DummyWebSocketListener()));
    }

    @Test
    public void testWebSocket_whenHandlerNull_thenThrowNullPointerException() {
        //"WebSocket handler class cannot be null");
        assertThrows(NullPointerException.class, () ->
            service.webSocket("/", null));
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.MILLISECONDS)
    public void stopExtinguishesServer() {
        final Routes routes = Routes.create();

        final EmbeddedServer embeddedServer = embeddedJettyFactory.create(routes, StaticFilesConfiguration.create(), new ExceptionMapper<>(),false);

        service.routes = routes;
        service.server = embeddedServer;
        service.init();
        service.stop();
        service.awaitStop();

        embeddedServer.extinguish();
        assertFalse(service.isInitialized());
    }

    @Test
    public void awaitStopBlocksUntilExtinguished() {
        final Routes routes = Routes.create();

        final EmbeddedServer embeddedServer = embeddedJettyFactory.create(routes, StaticFilesConfiguration.create(), new ExceptionMapper<>(),false);


        service.server = embeddedServer;
        service.routes = routes;
        service.init();
        service.stop();
        service.awaitStop();
        embeddedServer.extinguish();
        assertFalse(service.isInitialized());
    }

    @WebSocket
    protected static class DummyWebSocketListener {
    }
}
