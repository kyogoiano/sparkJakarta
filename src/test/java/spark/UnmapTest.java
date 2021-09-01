package spark;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.util.SparkTestUtil;

import static spark.Spark.*;
import static spark.Spark.awaitStop;

import static org.junit.jupiter.api.Assertions.*;
public class UnmapTest {

    final SparkTestUtil testUtil = new SparkTestUtil(4567);

    @Test
    public void testUnmap() throws Exception {
        get("/tobeunmapped", (q, a) -> "tobeunmapped");
        init();
        awaitInitialization();

        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/tobeunmapped", null);
        assertEquals(200, response.status);
        assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        assertEquals(404, response.status);

        get("/tobeunmapped", (q, a) -> "tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        assertEquals(200, response.status);
        assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped", "get");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        assertEquals(404, response.status);
    }

    @AfterAll
    public static void tearDown() {
        stop();
        awaitStop();
    }
}
