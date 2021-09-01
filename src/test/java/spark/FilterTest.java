package spark;

import java.io.IOException;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.util.SparkTestUtil;
import spark.util.SparkTestUtil.UrlResponse;

import static org.junit.jupiter.api.Assertions.*;
import static spark.Spark.*;

public class FilterTest {
    static SparkTestUtil testUtil;

    @AfterAll
    public static void tearDown() {
        stop();
        awaitStop();
    }

    @BeforeAll
    public static void setup() throws IOException {
        testUtil = new SparkTestUtil(4567);

        before("/justfilter", (q, a) -> System.out.println("Filter matched"));
        init();
        awaitInitialization();
    }

    @Test
    public void testJustFilter() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", "/justfilter", null);

        System.out.println("response.status = " + response.status);
        assertEquals(404, response.status);
    }

}
