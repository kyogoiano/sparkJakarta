package sparkTest.embeddedserver.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.JettyServer;

import static org.junit.jupiter.api.Assertions.*;

public class JettyServerTest {
    @Test
    public void testCreateServer_useDefaults() {
        Server server = new JettyServer().create(0, 0, 0);

        QueuedThreadPool threadPool = (QueuedThreadPool) server.getThreadPool();

        int minThreads = threadPool.getMinThreads();
        int maxThreads = threadPool.getMaxThreads();
        int idleTimeout = threadPool.getIdleTimeout();

        assertEquals(8, minThreads, "Server thread pool default minThreads should be 8");
        assertEquals(200, maxThreads, "Server thread pool default maxThreads should be 200");
        assertEquals(60000, idleTimeout, "Server thread pool default idleTimeout should be 60000");
    }

    @Test
    public void testCreateServer_whenNonDefaultMaxThreadsOnly_thenUseDefaultMinThreadsAndTimeout() {
        Server server = new JettyServer().create(9, 0, 0);

        QueuedThreadPool threadPool = (QueuedThreadPool) server.getThreadPool();

        int minThreads = threadPool.getMinThreads();
        int maxThreads = threadPool.getMaxThreads();
        int idleTimeout = threadPool.getIdleTimeout();

        assertEquals(8, minThreads, "Server thread pool default minThreads should be 8");
        assertEquals(9, maxThreads, "Server thread pool default maxThreads should be the same as specified");
        assertEquals(60000, idleTimeout, "Server thread pool default idleTimeout should be 60000");

    }

    @Test
    public void testCreateServer_whenNonDefaultMaxThreads_isLessThanDefaultMinThreads() {
        try {
            new JettyServer().create(2, 0, 0);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("max threads (2) less than min threads (8)", expected.getMessage());
        }
    }

    @Test
    public void testCreateServer_whenCustomMaxMinAndTimeoutProvided() {
        Server server = new JettyServer().create(50, 12, 30000);

        QueuedThreadPool threadPool = (QueuedThreadPool) server.getThreadPool();

        assertEquals(12, threadPool.getMinThreads());
        assertEquals(50, threadPool.getMaxThreads());
        assertEquals(30000, threadPool.getIdleTimeout());
    }

    @Test
    public void testCreateServer_withSuppliedThreadPoolAndNullThreadPool() {
        QueuedThreadPool customPool = new QueuedThreadPool(32, 4, 15000);
        Server serverWithCustomPool = new JettyServer().create(customPool);
        assertSame(customPool, serverWithCustomPool.getThreadPool());

        Server serverWithNullPool = new JettyServer().create(null);
        assertNotNull(serverWithNullPool.getThreadPool());
    }
}
