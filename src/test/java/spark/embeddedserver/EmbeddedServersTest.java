package spark.embeddedserver;

import java.io.File;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spark.Spark;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.JettyServerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EmbeddedServersTest {

    @Test
    public void testAddAndCreate_whenCreate_createsCustomServer(@TempDir File requestLogDir) throws Exception {
        // Create custom Server
        Server server = new Server();
        File requestLogFile = new File(requestLogDir, "request.log");
        server.setRequestLog(new CustomRequestLog(requestLogFile.getAbsolutePath()));
        JettyServerFactory serverFactory = mock(JettyServerFactory.class);
        when(serverFactory.create(0, 0, 0)).thenReturn(server);

        String id = "custom";

        // Register custom server
        EmbeddedServers.add(id, new EmbeddedJettyFactory(serverFactory));
        EmbeddedServer embeddedServer = EmbeddedServers.create(id, null, null, null, false);
        assertNotNull(embeddedServer);

        embeddedServer.trustForwardHeaders(true);
        embeddedServer.ignite("localhost", 0, null, 0, 0, 0);

        assertTrue(requestLogFile.exists());
        embeddedServer.extinguish();
        verify(serverFactory).create(0, 0, 0);

        server.stop();
        server.destroy();
    }

    @Test
    public void testAdd_whenConfigureRoutes_createsCustomServer(@TempDir File requestLogDir) {
        final File requestLogFile = new File(requestLogDir, "request.log");
        final CustomRequestLog customRequestLog = new CustomRequestLog(requestLogFile.getAbsolutePath());

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
        customRequestLog.destroy(); //FIXME: even destroying the resource is not recovered as the test scope ends
    }

    @AfterAll
    public static void tearDown() {
        Spark.stop();
    }

}
