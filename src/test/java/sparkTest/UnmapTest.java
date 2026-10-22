package sparkTest;


import org.junit.jupiter.api.*;
import sparkTest.util.SparkTestUtil;

import java.io.IOException;

import static spark.Spark.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UnmapTest {

    SparkTestUtil testUtil;

    @BeforeAll
    public void setup(){
        testUtil = new SparkTestUtil(4567);
    }
    @Test
    public void testUnmap() throws Exception {
        get("/tobeunmapped", (q, a) -> "tobeunmapped");
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
    public void tearDown() throws IOException {
        testUtil.closeClient();
        stop();
        awaitStop();
    }
}
