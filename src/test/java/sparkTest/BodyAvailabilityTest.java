package sparkTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Spark;
import sparkTest.util.SparkTestUtil;

import java.io.IOException;

import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.post;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BodyAvailabilityTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BodyAvailabilityTest.class);

    private static final String BODY_CONTENT = "the body content";
    
    private SparkTestUtil testUtil;

    private final int HTTP_OK = 200;
    
    private static String beforeBody = null;
    private static String routeBody = null;
    private static String afterBody = null;

    @AfterAll
    public void tearDown() throws IOException {
        testUtil.closeClient();
        Spark.stop();
        Spark.awaitStop();
        beforeBody = null;
        routeBody = null;
        afterBody = null;
    }

    @BeforeAll
    public void setup() {
        LOGGER.debug("setup()");

        testUtil = new SparkTestUtil(4567);

        beforeBody = null;
        routeBody = null;
        afterBody = null;

        before("/hello", (req, res) -> {
            LOGGER.debug("before-req.body() = {}", req.body());
            beforeBody = req.body();
        });

        post("/hello", (req, res) -> {
            LOGGER.debug("get-req.body() = {}", req.body());
            routeBody = req.body();
            return req.body();
        });

        after("/hello", (req, res) -> {
            LOGGER.debug("after-before-req.body() = {}", req.body());
            afterBody = req.body();
        });

        Spark.awaitInitialization();
    }

    @Test
    public void testPost() throws Exception {
        System.setProperty("org.eclipse.jetty.LEVEL", "DEBUG");
        SparkTestUtil.UrlResponse response = testUtil.doMethod("POST", "/hello", BODY_CONTENT);
        LOGGER.info(response.body);
        assertEquals(HTTP_OK, response.status);
        assertTrue(response.body.contains(BODY_CONTENT));

        assertEquals(BODY_CONTENT, beforeBody);
        assertEquals(BODY_CONTENT, routeBody);
        assertEquals(BODY_CONTENT, afterBody);
    }
}
