package sparkTest;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import spark.Spark;
import spark.SseEmitter;
import spark.servlet.SparkApplication;
import spark.servlet.SparkFilter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseServletContainerIntegrationTest {

    private final Server server = new Server();
    private int port;

    @AfterEach
    void stopServer() throws Exception {
        if (server.isRunning()) {
            server.stop();
            server.join();
        }
    }

    @Test
    void streamsThroughSparkFilterInAnExternalServletContainer() throws Exception {
        startServer(true);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/external"))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertEquals("ran", response.headers().firstValue("X-SSE-Before").orElseThrow());
        assertEquals("ran", response.headers().firstValue("X-SSE-After").orElseThrow());
        assertTrue(response.body().contains("data:external event"));
        assertTrue(ExternalApplication.emitter.get().isClosed());
    }

    @Test
    void externalServletFilterMustEnableAsyncSupport() throws Exception {
        startServer(false);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/external"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertTrue(ExternalApplication.emitter.get().isClosed());
    }

    private void startServer(boolean asyncSupported) throws Exception {
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler();
        FilterHolder filter = new FilterHolder(new TestSparkFilter());
        filter.setAsyncSupported(asyncSupported);
        context.addFilter(filter, "/*", EnumSet.of(DispatcherType.REQUEST));
        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    private static final class TestSparkFilter extends SparkFilter {
        @Override
        protected SparkApplication[] getApplications(FilterConfig filterConfig) {
            return new SparkApplication[]{new ExternalApplication()};
        }
    }

    public static final class ExternalApplication implements SparkApplication {
        private static final AtomicReference<SseEmitter> emitter = new AtomicReference<>();

        @Override
        public void init() {
            Spark.before("/external", (request, response) -> response.header("X-SSE-Before", "ran"));
            Spark.after("/external", (request, response) -> response.header("X-SSE-After", "ran"));
            Spark.sse("/external", (request, response, currentEmitter) -> {
                emitter.set(currentEmitter);
                currentEmitter.send("external event");
                currentEmitter.complete();
            });
        }

        @Override
        public void destroy() {
            spark.route.ServletRoutes.get().clear();
        }
    }
}
