package spark;


import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServers;
import spark.route.Routes;
import spark.ssl.SslStores;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static spark.Service.ignite;

public class ServiceTest {

    private static final String IP_ADDRESS = "127.0.0.1";
    private static final int NOT_FOUND_STATUS_CODE = HttpServletResponse.SC_NOT_FOUND;

    private Service service;

    private static String errorMessage = "";

    @BeforeEach
    public void test() {
        service = ignite();
        service.port(Service.SPARK_DEFAULT_PORT);
        service.initExceptionHandler((e) -> errorMessage = "Custom init error");
        service.init();
        service.awaitInitialization();
    }

    @AfterEach
    public void tearDown() {
        service.server.extinguish();
        service.stop();
        service.awaitStop();
    }


    @Test
    public void testEmbeddedServerIdentifier_defaultAndSet() {
        service.initialized = false;

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

        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.embeddedServerIdentifier(obj), "This must be done before route mapping has begun");
    }

    @Test()
    public void testHalt_whenOutParameters_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt());
    }

    @Test()
    public void testHalt_whenStatusCode_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt(NOT_FOUND_STATUS_CODE));
    }

    @Test()
    public void testHalt_whenBodyContent_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt("error"));
    }

    @Test()
    public void testHalt_whenStatusCodeAndBodyContent_thenThrowHaltException() {
        assertThrows(HaltException.class, () -> service.halt(NOT_FOUND_STATUS_CODE, "error"));
    }

    @Test
    public void testIpAddress_whenInitializedFalse() {
        service.initialized = false;
        service.ipAddress(IP_ADDRESS);

        String ipAddress = Whitebox.getInternalState(service, "ipAddress");
        assertEquals(IP_ADDRESS, ipAddress, "IP address should be set to the IP address that was specified");
    }

    @Test
    public void testIpAddress_whenInitializedTrue_thenThrowIllegalStateException() {

        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.ipAddress(IP_ADDRESS), "This must be done before route mapping has begun");
    }

    @Test
    public void testSetIpAddress_whenInitializedFalse() {
        service.initialized = false;
        service.ipAddress(IP_ADDRESS);

        String ipAddress = Whitebox.getInternalState(service, "ipAddress");
        assertEquals(IP_ADDRESS, ipAddress,"IP address should be set to the IP address that was specified");
    }

    @Test
    public void testSetIpAddress_whenInitializedTrue_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.ipAddress(IP_ADDRESS), "This must be done before route mapping has begun");
    }

    @Test
    public void testPort_whenInitializedFalse() {
        service.initialized = false;
        service.port(8080);

        int port = Whitebox.getInternalState(service, "port");
        assertEquals( 8080, port, "Port should be set to the Port that was specified");
    }

    @Test
    public void testPort_whenInitializedTrue_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.port(8080), "This must be done before route mapping has begun");
    }

    @Test
    public void testSetPort_whenInitializedFalse() {
        service.initialized = false;
        service.port(8080);

        int port = Whitebox.getInternalState(service, "port");
        assertEquals( 8080, port, "Port should be set to the Port that was specified");
    }

    @Test
    public void testSetPort_whenInitializedTrue_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.port(8080), "This must be done before route mapping has begun");
    }

    @Test
    public void testGetPort_whenInitializedFalse_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", false);
        assertThrows(IllegalStateException.class, () -> service.port(), "This must be done before route mapping has begun");
    }

    @Test
    public void testGetPort_whenInitializedTrue() {
        int expectedPort = 8080;
        Whitebox.setInternalState(service, "initialized", true);
        Whitebox.setInternalState(service, "port", expectedPort);

        int actualPort = service.port();

        assertEquals(expectedPort, actualPort, "Port retrieved should be the port setted");
    }

    @Test
    public void testGetPort_whenInitializedTrue_Default() {
        int expectedPort = Service.SPARK_DEFAULT_PORT;
        Whitebox.setInternalState(service, "initialized", true);

        int actualPort = service.port();

        assertEquals(expectedPort, actualPort, "Port retrieved should be the port setted");
    }

    @Test
    public void testThreadPool_whenOnlyMaxThreads() {
        service.initialized = false;
        service.threadPool(100);
        int maxThreads = Whitebox.getInternalState(service, "maxThreads");
        int minThreads = Whitebox.getInternalState(service, "minThreads");
        int threadIdleTimeoutMillis = Whitebox.getInternalState(service, "threadIdleTimeoutMillis");
        assertEquals(100, maxThreads, "Should return maxThreads specified");
        assertEquals(-1, minThreads, "Should return minThreads specified");
        assertEquals(-1, threadIdleTimeoutMillis, "Should return threadIdleTimeoutMillis specified");
    }

    @Test
    public void testThreadPool_whenMaxMinAndTimeoutParameters() {
        service.initialized = false;
        service.threadPool(100, 50, 75);
        int maxThreads = Whitebox.getInternalState(service, "maxThreads");
        int minThreads = Whitebox.getInternalState(service, "minThreads");
        int threadIdleTimeoutMillis = Whitebox.getInternalState(service, "threadIdleTimeoutMillis");
        assertEquals( 100, maxThreads, "Should return maxThreads specified");
        assertEquals( 50, minThreads, "Should return minThreads specified");
        assertEquals(75, threadIdleTimeoutMillis, "Should return threadIdleTimeoutMillis specified");
    }

    @Test
    public void testThreadPool_whenMaxMinAndTimeoutParameters_thenThrowIllegalStateException() {
        service.initialized = false;
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.threadPool(100, 50, 75),
            "This must be done before route mapping has begun");
    }

    @Test
    public void testSecure_thenReturnNewSslStores() {
        service.initialized = false;
        service.secure("keyfile", "keypassword", "truststorefile", "truststorepassword");
        SslStores sslStores = Whitebox.getInternalState(service, "sslStores");
        assertNotNull(sslStores, "Should return a SslStores because we configured it to have one");
        assertEquals("keyfile", sslStores.keystoreFile(), "Should return keystoreFile from SslStores");
        assertEquals( "keypassword", sslStores.keystorePassword(), "Should return keystorePassword from SslStores");
        assertEquals( "truststorefile", sslStores.trustStoreFile(), "Should return trustStoreFile from SslStores");
        assertEquals( "truststorepassword", sslStores.trustStorePassword(), "Should return trustStorePassword from SslStores");
    }

    @Test
    public void testSecure_whenInitializedTrue_thenThrowIllegalStateException() {

        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.secure(null, null, null, null),
            "This must be done before route mapping has begun");
    }

    @Test
    public void testSecure_whenInitializedFalse_thenThrowIllegalArgumentException() {
        service.initialized = false;
        assertThrows(IllegalArgumentException.class, () -> service.secure(null, null, null, null),
            "Must provide a keystore file to run secured");
    }

    @Test
    public void testWebSocketIdleTimeoutMillis_whenInitializedTrue_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.webSocketIdleTimeoutMillis(100), "This must be done before route mapping has begun");
    }

    @Test
    public void testWebSocket_whenInitializedTrue_thenThrowIllegalStateException() {
        Whitebox.setInternalState(service, "initialized", true);
        assertThrows(IllegalStateException.class, () -> service.webSocket("/", DummyWebSocketListener.class),
            "This must be done before route mapping has begun");
    }
    
    @Test
    public void testWebSocket_whenPathNull_thenThrowNullPointerException() {
        service.initialized = false;
        assertThrows(NullPointerException.class , () -> service.webSocket(null, new DummyWebSocketListener()), "WebSocket path cannot be null");
    }
    
    @Test
    public void testWebSocket_whenHandlerNull_thenThrowNullPointerException() {
        assertThrows(NullPointerException.class , () -> service.webSocket("/", null),
            "WebSocket handler class cannot be null");
    }
    
    @Test()
    public void stopExtinguishesServer() {
        Service service = Service.ignite();
        Routes routes = Mockito.mock(Routes.class);
        EmbeddedServer server = Mockito.mock(EmbeddedServer.class);
        service.routes = routes;
        service.server = server;
        service.initialized = true;
        assertTimeout(Duration.of(300L, ChronoUnit.MILLIS), service::stop);
        try {
        	// yes, this is ugly and forces to set a test timeout as a precaution :(
            while (service.initialized) {
            	Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Mockito.verify(server).extinguish();
    }
    
    @Test
    public void awaitStopBlocksUntilExtinguished() {
        Service service = Service.ignite();
        Routes routes = Mockito.mock(Routes.class);
        EmbeddedServer server = Mockito.mock(EmbeddedServer.class);
        service.routes = routes;
        service.server = server;
        service.initialized = true;
        service.stop();
        service.awaitStop();
        Mockito.verify(server).extinguish();
        assertFalse(service.initialized);
    }
    
    @WebSocket
    protected static class DummyWebSocketListener {
    }
}
