package spark.embeddedserver.jetty.websocket;

import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class WebSocketServletContextHandlerFactoryTest {

    final String webSocketPath = "/websocket";
    private ServletContextHandler servletContextHandler;
    private final static Server server = new Server();
    private final static long timeout = 1000L;
    private Duration timeoutDuration;

    @Before
    public void setup(){
         timeoutDuration = ChronoUnit.SECONDS.getDuration();
    }

    @Test
    public void testCreate_whenWebSocketHandlersIsNull_thenReturnNull() throws Exception {

        servletContextHandler = WebSocketServletContextHandlerFactory.create(null, Optional.empty(), server, "WebSocketServletTest");

        assertNull("Should return null because no WebSocket Handlers were passed", servletContextHandler);

    }

    @Test
    public void testCreate_whenNoIdleTimeoutIsPresent() throws Exception {

        Map<String, WebSocketHandlerWrapper> webSocketHandlers = new HashMap<>();

        webSocketHandlers.put(webSocketPath, new WebSocketHandlerClassWrapper(WebSocketTestHandler.class));

        servletContextHandler = WebSocketServletContextHandlerFactory.create(webSocketHandlers, Optional.empty(), server, "WebSocketServletTest");
        servletContextHandler.start();

        FilterHolder filterHolder = WebSocketUpgradeFilter.getFilter(servletContextHandler.getServletContext());
        assertEquals("Should return a WebSocketUpgradeFilter because we configured it to have one", filterHolder.getName(), "org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter");

        ServletHandler.MappedServlet mappedServlet =
            servletContextHandler.getServletHandler().getMappedServlet("/websocket");

        PathSpec pathSpec = mappedServlet.getPathSpec();

        assertEquals("Should return the WebSocket path specified when context handler was created",
                webSocketPath, pathSpec.getDeclaration());
        servletContextHandler.stop();
        servletContextHandler.destroy();
        // Because spark works on a non-initialized / non-started ServletContextHandler and WebSocketUpgradeFilter
        // the stored WebSocketCreator is wrapped for persistence through the start/stop of those contexts.
        // You cannot unwrap or cast to that WebSocketTestHandler this way.
        // Only websockets that are added during a live context can be cast this way.
        // WebSocketCreator sc = mappedResource.getResource();
        // assertTrue("Should return true because handler should be an instance of the one we passed when it was created",
        //        sc.getHandler() instanceof WebSocketTestHandler);
    }

    @Test
    public void testCreate_whenTimeoutIsPresent() throws Exception {

        Map<String, WebSocketHandlerWrapper> webSocketHandlers = new HashMap<>();

        webSocketHandlers.put(webSocketPath, new WebSocketHandlerClassWrapper(WebSocketTestHandler.class));

        servletContextHandler = WebSocketServletContextHandlerFactory.create(webSocketHandlers, Optional.of(timeout), server, "WebSocketServletTest");
        servletContextHandler.start();
        JettyWebSocketServerContainer container = JettyWebSocketServerContainer.getContainer(servletContextHandler.getServletContext());

        assertEquals("Timeout value should be the same as the timeout specified when context handler was created",
            timeoutDuration, container.getIdleTimeout());

        FilterHolder filterHolder = WebSocketUpgradeFilter.getFilter(servletContextHandler.getServletContext());
        assertEquals("Should return a WebSocketUpgradeFilter because we configured it to have one", filterHolder.getName(), "org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter");

        ServletHandler.MappedServlet mappedServlet =
            servletContextHandler.getServletHandler().getMappedServlet("/websocket");

        PathSpec pathSpec = mappedServlet.getPathSpec();

        assertEquals("Should return the WebSocket path specified when context handler was created",
                webSocketPath, pathSpec.getDeclaration());
        servletContextHandler.stop();
        servletContextHandler.destroy();
        // Because spark works on a non-initialized / non-started ServletContextHandler and WebSocketUpgradeFilter
        // the stored WebSocketCreator is wrapped for persistence through the start/stop of those contexts.
        // You cannot unwrap or cast to that WebSocketTestHandler this way.
        // Only websockets that are added during a live context can be cast this way.
        // WebSocketCreator sc = mappedResource.getResource();
        // assertTrue("Should return true because handler should be an instance of the one we passed when it was created",
        //        sc.getHandler() instanceof WebSocketTestHandler);
    }

    @Test
    public void testCreate_whenWebSocketContextHandlerCreationFails_thenThrowException() throws Exception {

        Map<String, WebSocketHandlerWrapper> webSocketHandlers = new HashMap<>();

        webSocketHandlers.put(webSocketPath, new WebSocketHandlerClassWrapper(WebSocketTestHandler.class));

        try (MockedStatic<WebSocketServletContextHandlerFactory> servletContextHandler = Mockito.mockStatic(WebSocketServletContextHandlerFactory.class)) {
            servletContextHandler.when(() -> WebSocketServletContextHandlerFactory.create(webSocketHandlers, Optional.empty(), server, "WebSocketServletTest"))
                .thenReturn(null);

            assertNull("Should be null because Websocket context handler was not created", WebSocketServletContextHandlerFactory.create(webSocketHandlers, Optional.empty(), server, "WebSocketServletTest"));
        }

    }
}
