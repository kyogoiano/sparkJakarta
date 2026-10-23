package sparkTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Spark;
import sparkTest.util.SparkTestUtil;
import sparkTest.util.SparkTestUtil.UrlResponse;

import java.io.IOException;

import static spark.Spark.*;

import static org.junit.jupiter.api.Assertions.*;



public class ResponseWrapperDelegationTest {

    static SparkTestUtil testUtil;

    @AfterAll
    public static void tearDown() {
        Spark.stop();
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

        get("/wrapper-cookie", (q, a) -> {
            a.cookie("wrapperCookie", "val1", 60, true, true, "Strict");
            return "cookie-ok";
        });

        after("/wrapper-cookie", (q, a) -> {
            a.cookie("localhost", "/wrapper-cookie", "afterCookie", "val2", 60, true, true,
                    java.util.Map.of("SameSite", "Lax"));
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
        assertEquals("text/plain;charset=iso-8859-1", response.headers.get("Content-Type"));
    }

    @Test
    public void wrapper_delegates_cookie_attributes_in_routes_and_filters() throws Exception {
        UrlResponse response = testUtil.get("/wrapper-cookie");
        assertEquals(200, response.status);
        assertEquals("cookie-ok", response.body);
        assertNotNull(response.headers.get("Set-Cookie"));
    }
}
