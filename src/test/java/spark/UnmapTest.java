package spark;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import spark.util.SparkTestUtil;

import static spark.Spark.*;
import static spark.Spark.awaitStop;

public class UnmapTest {

    SparkTestUtil testUtil = new SparkTestUtil(4567);

    @Test
    public void testUnmap() throws Exception {
        get("/tobeunmapped", (q, a) -> "tobeunmapped");
        awaitInitialization();

        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(200, response.status);
        Assert.assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(404, response.status);

        get("/tobeunmapped", (q, a) -> "tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(200, response.status);
        Assert.assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped", "get");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(404, response.status);
    }

    @AfterClass
    public static void tearDown() {
        stop();
        awaitStop();
    }
}
