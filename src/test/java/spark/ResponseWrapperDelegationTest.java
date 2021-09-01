package spark;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.util.SparkTestUtil;
import spark.util.SparkTestUtil.UrlResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static spark.Spark.*;

public class ResponseWrapperDelegationTest {

    static SparkTestUtil testUtil;

    @AfterAll
    public static void tearDown() {
        Spark.stop();
        Spark.awaitStop();
    }

    @BeforeAll
    public static void setup() throws IOException {
        testUtil = new SparkTestUtil(4567);

        get("/204", (q, a) -> {
            a.status(204);
            return "";
        });

        after("/204", (q, a) -> {
            if (a.status() == 204) {
                a.status(200);
                a.body("ok");
            }
        });

        get("/json", (q, a) -> {
            a.type("application/json");
            return "{\"status\": \"ok\"}";
        });

        after("/json", (q, a) -> {
            if ("application/json".equalsIgnoreCase(a.type())) {
                a.type("text/plain");
            }
        });

        exception(Exception.class, (exception, q, a) -> exception.printStackTrace());

        Spark.awaitInitialization();
    }

    @Test
    public void filters_can_detect_response_status() throws Exception {
        UrlResponse response = testUtil.get("/204");
        assertEquals(200, response.status);
        assertEquals("ok", response.body);
    }

    @Test
    public void filters_can_detect_content_type() throws Exception {
        UrlResponse response = testUtil.get("/json");
        assertEquals(200, response.status);
        assertEquals("{\"status\": \"ok\"}", response.body);
        assertEquals("text/plain;charset=utf-8", response.headers.get("Content-Type"));
    }
}
