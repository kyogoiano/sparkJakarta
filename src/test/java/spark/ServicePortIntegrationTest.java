package spark;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.util.SparkTestUtil;

import static spark.Service.ignite;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Created by Tom on 08/02/2017.
 */
public class ServicePortIntegrationTest {

    private static Service service;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServicePortIntegrationTest.class);

    @BeforeAll
    public static void setUpClass() {
        service = ignite();
        service.port(0);

        service.get("/hi", (q, a) -> "Hello World!");

        service.awaitInitialization();
    }

    @Test
    public void testGetPort_withRandomPort() throws Exception {
        int actualPort = service.port();

        LOGGER.info("got port ");

        SparkTestUtil testUtil = new SparkTestUtil(actualPort);

        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/hi", null);
        assertEquals(200, response.status);
        assertEquals("Hello World!", response.body);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        service.stop();
    }
}
