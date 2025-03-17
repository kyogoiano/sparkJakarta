package sparkTest.servlet;

import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee9.webapp.WebAppContext;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Spark;
import sparkTest.util.SparkTestUtil;
import sparkTest.util.SparkTestUtil.UrlResponse;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServletTest {

    private static final String SOMEPATH = "/somepath";
    private static final int PORT = 9393;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServletTest.class);

    private SparkTestUtil testUtil;
    private Server server;

    @AfterAll
    public void tearDown() throws Exception {
        testUtil.closeClient();
        LOGGER.info(">>> STOPPING EMBEDDED JETTY SERVER");
        Spark.stop();
        Spark.awaitStop();

        if (MyApp.tmpExternalFile != null) {
            LOGGER.debug("tearDown().deleting: {}", MyApp.tmpExternalFile);
            MyApp.tmpExternalFile.delete();
        }
    }

    @BeforeAll
    @Timeout(value = 3000)
    public void setup() throws Exception {
        server = new Server();
        try(final ServerConnector connector = new ServerConnector(server)){
            // Set some timeout options to make debugging easier.
            connector.setIdleTimeout(1000 * 60 * 60);
            connector.setShutdownIdleTimeout(-1);
            connector.setPort(PORT);
            server.addConnector(connector);
        }

        WebAppContext bb = new WebAppContext();
        bb.setContextPath(SOMEPATH);
        bb.setWar("src/test/webapp");


        //ServletContextHandler handler = new ServletContextHandler(server, SOMEPATH);
        server.setHandler(bb);



        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                LOGGER.info(">>> STARTING EMBEDDED JETTY SERVER for jUnit testing of SparkFilter");
                server.start();
                //System.in.read();
                //LOGGER.info(">>> STOPPING EMBEDDED JETTY SERVER");
                //server.stop();
                latch.countDown();
                server.join();
                server.stop();
                server.destroy();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(100);
            }
        }).start();

        latch.await();

        testUtil = new SparkTestUtil(PORT);
    }

    @Test
    public void testStaticResource() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/css/style.css", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertTrue(response.body.contains("Content of css file"));
    }

    @Test
    public void testStaticWelcomeResource() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/pages/", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertTrue(response.body.contains("<html><body>Hello Static World!</body></html>"));
    }

    @Test
    public void testExternalStaticFile() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/" + MyApp.EXTERNAL_FILE, null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("Content of external file", response.body);
    }

    @Test
    public void testGetHi() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/hi", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("Hello World!", response.body);
    }

    @Test
    public void testHiHead() throws Exception {
        UrlResponse response = testUtil.doMethod("HEAD", SOMEPATH + "/hi", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("", response.body);
    }

    @Test
    public void testGetHiAfterFilter() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/hi", null);
        Assertions.assertTrue(response.headers.get("after").contains("foobar"));
    }

    @Test
    public void testGetRoot() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("Hello Root!", response.body);
    }

    @Test
    public void testEchoParam1() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/shizzy", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("echo: shizzy", response.body);
    }

    @Test
    public void testEchoParam2() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", SOMEPATH + "/gunit", null);
        Assertions.assertEquals(200, response.status);
        Assertions.assertEquals("echo: gunit", response.body);
    }

    @Test
    public void testUnauthorized() throws Exception {
        UrlResponse urlResponse = testUtil.doMethod("GET", SOMEPATH + "/protected/resource", null);
        Assertions.assertEquals(401, urlResponse.status);
    }

    @Test
    public void testNotFound() throws Exception {
        UrlResponse urlResponse = testUtil.doMethod("GET", SOMEPATH + "/no/resource", null);
        Assertions.assertEquals(404, urlResponse.status);
    }

    @Test
    public void testPost() throws Exception {
        UrlResponse response = testUtil.doMethod("POST", SOMEPATH + "/poster", "Fo shizzy");
        Assertions.assertEquals(201, response.status);
        Assertions.assertTrue(response.body.contains("Fo shizzy"));
    }
}
