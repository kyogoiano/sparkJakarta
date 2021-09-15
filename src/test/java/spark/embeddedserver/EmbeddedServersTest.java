package spark.embeddedserver;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spark.Service;
import spark.Spark;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.JettyServerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static spark.Service.ignite;

@Disabled
public class EmbeddedServersTest {

    @TempDir
    public File temporaryFolder;

    @Test
    public void testAddAndCreate_whenCreate_createsCustomServer() throws Exception {
        // Create custom Server
        final Server server = new Server();
        File requestLogFile = new File(temporaryFolder, "request.log");
        server.setRequestLog(new CustomRequestLog(requestLogFile.getAbsolutePath()));
        JettyServerFactory serverFactory = mock(JettyServerFactory.class);
        when(serverFactory.create(0, 0, 0)).thenReturn(server);

        String id = "custom";

        // Register custom server
        EmbeddedServers.add(id, new EmbeddedJettyFactory(serverFactory));
        final EmbeddedServer embeddedServer = EmbeddedServers.create(id, null, null, null, false);
        assertNotNull(embeddedServer);

        embeddedServer.trustForwardHeaders(true);
        embeddedServer.ignite("localhost", 0, null, 0, 0, 0);

        assertTrue(requestLogFile.exists());
        embeddedServer.extinguish();
        verify(serverFactory).create(0, 0, 0);

    }

    @Test
    public void testAdd_whenConfigureRoutes_createsCustomServer() {

        final File requestLogFile = new File(temporaryFolder, "request.log");
        // Register custom server
        EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY, new EmbeddedJettyFactory(new JettyServerFactory() {
            @Override
            public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
                Server server = new Server();
                server.setRequestLog(new CustomRequestLog(requestLogFile.getAbsolutePath()));
                return server;
            }

            @Override
            public Server create(ThreadPool threadPool) {
                return new Server(new QueuedThreadPool(10));
            }
        }));
        Spark.get("/", (request, response) -> "OK");

//        EmbeddedServers.initialize();
//
//        Spark.awaitInitialization();

        Service service = ignite();
        service.port(8080);
        service.init();
        service.awaitInitialization();

        assertTrue(requestLogFile.exists());

    }

    @AfterAll
    public static void tearDown() {
        Spark.stop();
        Spark.awaitStop();
    }

}
