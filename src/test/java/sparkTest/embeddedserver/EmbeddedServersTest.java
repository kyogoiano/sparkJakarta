package sparkTest.embeddedserver;

import java.io.File;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import spark.Spark;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.JettyServerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmbeddedServersTest {

    @TempDir File requestLogDir;
    File requestLogFile;
    CustomRequestLog customRequestLog;

    @Test
    public void testAddAndCreate_whenCreate_createsCustomServer() throws Exception {
        // Create custom Server
        Server server = new Server();
        server.setRequestLog(customRequestLog);
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
        customRequestLog.stop();
        customRequestLog.destroy();
        embeddedServer.extinguish();
        verify(serverFactory).create(0, 0, 0);

        server.stop();
        server.destroy();
    }

    @Test
    public void testAdd_whenConfigureRoutes_createsCustomServer() throws Exception {

        // Register custom server
        EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY, new EmbeddedJettyFactory(new JettyServerFactory() {
            @Override
            public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
                Server server = new Server();
                server.setRequestLog(customRequestLog);
                return server;
            }

            @Override
            public Server create(ThreadPool threadPool) {
                return null;
            }
        }));
        Spark.get("/", (request, response) -> "OK");
        Spark.awaitInitialization();

        assertTrue(requestLogFile.exists());

        EmbeddedServers.clear();
    }

    @AfterAll
    public void tearDown() {
        Spark.stop();
        Spark.awaitStop();
    }

    @BeforeEach
    public void buildLog() {
        requestLogFile = new File(requestLogDir, "request.log");
        customRequestLog = new CustomRequestLog(requestLogFile.getAbsolutePath());
    }

    @AfterEach
    public void cleanLog() throws Exception {
        customRequestLog.stop();
        customRequestLog.destroy();
    }

}
