package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.websocket.jakarta.server.JakartaWebSocketServerContainer;
import org.eclipse.jetty.ee11.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.ee11.websocket.servlet.WebSocketUpgradeFilter;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerClassWrapper;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerWrapper;
import spark.embeddedserver.jetty.websocket.WebSocketServletContextHandlerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketServletContextHandlerFactoryTest {

    private static final String WEB_SOCKET_PATH = "/websocket";
    private static final long TIMEOUT_MILLIS = 1000L;

    private Server server;
    private ServletContextHandler servletContextHandler;

    @BeforeEach
    public void setup() {
        server = new Server();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (servletContextHandler != null) {
            if (servletContextHandler.isRunning()) {
                servletContextHandler.stop();
            }
            servletContextHandler.destroy();
        }
        if (server.getScheduler().isStarted()) {
            server.getScheduler().stop();
        }
        server.destroy();
    }

    @Test
    public void createWithoutEndpointsReturnsNull() {
        assertNull(WebSocketServletContextHandlerFactory.create(null, null, server));
    }

    @Test
    public void factoryLeavesServerHandlerInstallationToItsCaller() throws Exception {
        Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers = new HashMap<>();
        webSocketHandlers.put(WEB_SOCKET_PATH, new WebSocketHandlerClassWrapper<>(WebSocketTestHandler.class));

        servletContextHandler = WebSocketServletContextHandlerFactory.create(webSocketHandlers, null, server);

        assertNotNull(servletContextHandler);
        assertNull(server.getHandler());
        server.setHandler(servletContextHandler);
        server.getScheduler().start();
        servletContextHandler.start();

        FilterHolder filterHolder = WebSocketUpgradeFilter.getFilter(servletContextHandler.getServletContext());
        assertNotNull(filterHolder);
        assertEquals("org.eclipse.jetty.ee11.websocket.servlet.WebSocketUpgradeFilter", filterHolder.getName());
        var servletMapping = servletContextHandler.getServletHandler().getMatchedServlet(WEB_SOCKET_PATH);
        assertNotNull(servletMapping);
        assertNotEquals(WEB_SOCKET_PATH, servletMapping.getPathSpec().getDeclaration(),
                "The endpoint must not be registered to a dedicated no-op servlet");
    }

    @Test
    public void globalIdleTimeoutIsAppliedToBothWebSocketContainers() throws Exception {
        Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers = new HashMap<>();
        webSocketHandlers.put(WEB_SOCKET_PATH, new WebSocketHandlerClassWrapper<>(WebSocketTestHandler.class));
        Map<String, Class<?>> jakartaEndpoints = Map.of("/jakarta", JakartaAnnotatedEndpoint.class);

        servletContextHandler = WebSocketServletContextHandlerFactory.create(
                webSocketHandlers, jakartaEndpoints, null, TIMEOUT_MILLIS);
        server.setHandler(servletContextHandler);
        server.getScheduler().start();
        servletContextHandler.start();

        JettyWebSocketServerContainer jettyContainer =
                JettyWebSocketServerContainer.getContainer(servletContextHandler.getServletContext());
        ServerContainer jakartaContainer =
                (ServerContainer) servletContextHandler.getServletContext().getAttribute(ServerContainer.class.getName());

        assertEquals(Duration.ofMillis(TIMEOUT_MILLIS), jettyContainer.getIdleTimeout());
        assertNotNull(jakartaContainer);
        assertEquals(TIMEOUT_MILLIS, jakartaContainer.getDefaultMaxSessionIdleTimeout());
        assertNotNull(JakartaWebSocketServerContainer.getContainer(servletContextHandler.getServletContext()));
    }

    @ServerEndpoint("/jakarta")
    public static class JakartaAnnotatedEndpoint {
    }
}
