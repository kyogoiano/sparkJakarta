package spark.customerrorpages;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.CustomErrorPages;
import spark.Spark;
import spark.util.SparkTestUtil;

import static spark.Spark.get;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;

import static org.junit.jupiter.api.Assertions.*;

public class CustomErrorPagesTest {

    private static final String CUSTOM_NOT_FOUND = "custom not found 404";
    private static final String CUSTOM_INTERNAL = "custom internal 500";
    private static final String HELLO_WORLD = "hello world!";
    public static final String APPLICATION_JSON = "application/json";
    private static final String QUERY_PARAM_KEY = "qparkey";

    static SparkTestUtil testUtil;

    @AfterAll
    public static void tearDown() {
        Spark.stop();
        Spark.awaitStop();
    }

    @BeforeAll
    public static void setup() throws IOException {
        testUtil = new SparkTestUtil(4567);

        get("/hello", (q, a) -> HELLO_WORLD);

        get("/raiseinternal", (q, a) -> {
            throw new Exception("");
        });

        notFound(CUSTOM_NOT_FOUND);

        internalServerError((request, response) -> {
            if (request.queryParams(QUERY_PARAM_KEY) != null) {
                throw new Exception();
            }
            response.type(APPLICATION_JSON);
            return CUSTOM_INTERNAL;
        });

        Spark.awaitInitialization();
    }

    @Test
    public void testGetHi() throws Exception {
        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/hello", null);
        assertEquals(200, response.status);
        assertEquals(HELLO_WORLD, response.body);
    }

    @Test
    @Disabled
    public void testCustomNotFound() throws Exception {
        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/othernotmapped", null);
        assertEquals(404, response.status);
        assertEquals(CUSTOM_NOT_FOUND, response.body);
    }

    @Test
    public void testCustomInternal() throws Exception {
        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/raiseinternal", null);
        assertEquals(500, response.status);
        assertEquals(APPLICATION_JSON, response.headers.get("Content-Type"));
        assertEquals(CUSTOM_INTERNAL, response.body);
    }

    @Test
    public void testCustomInternalFailingRoute() throws Exception {
        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/raiseinternal?" + QUERY_PARAM_KEY + "=sumthin", null);
        assertEquals(500, response.status);
        assertEquals(CustomErrorPages.INTERNAL_ERROR, response.body);
    }

}
