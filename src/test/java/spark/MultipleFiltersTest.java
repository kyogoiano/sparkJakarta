package spark;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.util.SparkTestUtil;

import static spark.Spark.after;
import static spark.Spark.awaitInitialization;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.stop;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Basic test to ensure that multiple before and after filters can be mapped to a route.
 */
public class MultipleFiltersTest {
    
    private static SparkTestUtil http;


    @BeforeAll
    public static void setup() {
        http = new SparkTestUtil(4567);

        before("/user", initializeCounter, incrementCounter, loadUser);

        after("/user", incrementCounter, (req, res) -> {
            int counter = req.attribute("counter");
            assertEquals(counter, 2);
        });

        get("/user", (request, response) -> {
            assertEquals((int) request.attribute("counter"), 1);
            return ((User) request.attribute("user")).name();
        });

        awaitInitialization();
    }

    @AfterAll
    public static void stopServer() {
        stop();
    }

    @Test
    public void testMultipleFilters() {
        try {
            SparkTestUtil.UrlResponse response = http.get("/user");
            assertEquals(200, response.status);
            assertEquals("Kevin", response.body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final Filter loadUser = (request, response) -> {
        User u = new User();
        u.name("Kevin");
        request.attribute("user", u);
    };

    private static final Filter initializeCounter = (request, response) -> request.attribute("counter", 0);

    private static final Filter incrementCounter = (request, response) -> {
        int counter = request.attribute("counter");
        counter++;
        request.attribute("counter", counter);
    };

    private static class User {

        private String name;

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }
    }
}
