package sparkTest;

import static spark.Spark.*;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Service;
import spark.Spark;

import static org.junit.jupiter.api.Assertions.*;

import static spark.Service.ignite;
/**
 * System tests for the Cookies support.
 *
 * @author dreambrother
 */

public class CookiesIntegrationTest {

    private static final String DEFAULT_HOST_URL = "http://localhost:4567";
    private final HttpClient httpClient = HttpClientBuilder.create().build();
    private static String errorMessage = "";

    @BeforeAll
    public static void initRoutes() {
        Service service = ignite();
        service.port(8080);
        service.initExceptionHandler((e) -> errorMessage = "Custom init error");
        service.init();
        service.awaitInitialization();
        post("/assertNoCookies", (request, response) -> {
            if (!request.cookies().isEmpty()) {
                halt(500);
            }
            return "";
        });

        post("/setCookie", (request, response) -> {
            response.cookie(request.queryParams("cookieName"), request.queryParams("cookieValue"));
            return "";
        });

        post("/assertHasCookie", (request, response) -> {
            String cookieValue = request.cookie(request.queryParams("cookieName"));
            if (!request.queryParams("cookieValue").equals(cookieValue)) {
                halt(500);
            }
            return "";
        });

        post("/removeCookie", (request, response) -> {
            String cookieName = request.queryParams("cookieName");
            String cookieValue = request.cookie(cookieName);
            if (!request.queryParams("cookieValue").equals(cookieValue)) {
                halt(500);
            }
            response.removeCookie(cookieName);
            return "";
        });

        post("/path/setCookieWithPath", (request, response) -> {
            String cookieName = request.queryParams("cookieName");
            String cookieValue = request.queryParams("cookieValue");
            response.cookie("/path", cookieName, cookieValue, -1, false);
            return "";
        }) ;

        post("/path/removeCookieWithPath", (request, response) -> {
            String cookieName = request.queryParams("cookieName");
            String cookieValue = request.cookie(cookieName);
            if (!request.queryParams("cookieValue").equals(cookieValue)) {
                halt(500);
            }
            response.removeCookie("/path", cookieName);
            return "";
        }) ;

        post("/path/assertNoCookies", (request, response) -> {
            if (!request.cookies().isEmpty()) {
                halt(500);
            }
            return "";
        });

        post("/setCookieWithSameSite", (request, response) -> {
            response.cookie("strictCookie", "strictVal", 3600, true, true, "Strict");
            return "";
        });

        post("/setCookieWithPathAndSameSite", (request, response) -> {
            response.cookie("/api", "laxCookie", "laxVal", 1800, false, true, "Lax");
            return "";
        });

        post("/setCookieWithDomainPathAndSameSite", (request, response) -> {
            response.cookie("localhost", "/api", "noneCookie", "noneVal", 3600, true, true, "None");
            return "";
        });

        post("/setCookieWithCustomAttributes", (request, response) -> {
            response.cookie("localhost", "/api", "partitionedCookie", "partVal", 3600, true, true,
                    java.util.Map.of("SameSite", "None", "Partitioned", ""));
            return "";
        });

    }

    @AfterAll
    public static void stopServer() {
        Spark.stop();
        Spark.awaitStop();
    }

    @Test
    public void testEmptyCookies() {
        httpPost("/assertNoCookies");
    }

    @Test
    public void testCreateCookie() {
        String cookieName = "testCookie";
        String cookieValue = "testCookieValue";
        httpPost("/setCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue);
        httpPost("/assertHasCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue);
    }

    @Test
    public void testRemoveCookie() {
        String cookieName = "testCookie";
        String cookieValue = "testCookieValue";
        httpPost("/setCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue);
        httpPost("/removeCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue);
        httpPost("/assertNoCookies");
    }

    @Test
    public void testRemoveCookieWithPath() {
        String cookieName = "testCookie";
        String cookieValue = "testCookieValue";
        httpPost("/path/setCookieWithPath?cookieName=" + cookieName + "&cookieValue=" + cookieValue);

        // for sanity, check that cookie is not sent with request if path doesn't match
        httpPost("/assertNoCookies");

        // now remove cookie with matching path
        httpPost("/path/removeCookieWithPath?cookieName=" + cookieName + "&cookieValue=" + cookieValue);
        httpPost("/path/assertNoCookies");
    }

    @Test
    public void testCookieWithSameSiteHeader() throws Exception {
        HttpPost request = new HttpPost(DEFAULT_HOST_URL + "/setCookieWithSameSite");
        httpClient.execute(request, response -> {
            assertEquals(200, response.getCode());
            var setCookieHeader = response.getFirstHeader("Set-Cookie");
            assertNotNull(setCookieHeader);
            String headerVal = setCookieHeader.getValue();
            assertTrue(headerVal.contains("strictCookie=strictVal"), "Got: " + headerVal);
            assertTrue(headerVal.contains("SameSite=Strict"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Secure"), "Got: " + headerVal);
            assertTrue(headerVal.contains("HttpOnly"), "Got: " + headerVal);
            return null;
        });
    }

    @Test
    public void testCookieWithPathAndSameSiteHeader() throws Exception {
        HttpPost request = new HttpPost(DEFAULT_HOST_URL + "/setCookieWithPathAndSameSite");
        httpClient.execute(request, response -> {
            assertEquals(200, response.getCode());
            var setCookieHeader = response.getFirstHeader("Set-Cookie");
            assertNotNull(setCookieHeader);
            String headerVal = setCookieHeader.getValue();
            assertTrue(headerVal.contains("laxCookie=laxVal"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Path=/api"), "Got: " + headerVal);
            assertTrue(headerVal.contains("HttpOnly"), "Got: " + headerVal);
            assertTrue(headerVal.contains("SameSite=Lax"), "Got: " + headerVal);
            return null;
        });
    }

    @Test
    public void testCookieWithDomainPathAndSameSiteHeader() throws Exception {
        HttpPost request = new HttpPost(DEFAULT_HOST_URL + "/setCookieWithDomainPathAndSameSite");
        httpClient.execute(request, response -> {
            assertEquals(200, response.getCode());
            var setCookieHeader = response.getFirstHeader("Set-Cookie");
            assertNotNull(setCookieHeader);
            String headerVal = setCookieHeader.getValue();
            assertTrue(headerVal.contains("noneCookie=noneVal"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Domain=localhost"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Path=/api"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Secure"), "Got: " + headerVal);
            assertTrue(headerVal.contains("HttpOnly"), "Got: " + headerVal);
            assertTrue(headerVal.contains("SameSite=None"), "Got: " + headerVal);
            return null;
        });
    }

    @Test
    public void testCookieWithCustomAttributesMapHeader() throws Exception {
        HttpPost request = new HttpPost(DEFAULT_HOST_URL + "/setCookieWithCustomAttributes");
        httpClient.execute(request, response -> {
            assertEquals(200, response.getCode());
            var setCookieHeader = response.getFirstHeader("Set-Cookie");
            assertNotNull(setCookieHeader);
            String headerVal = setCookieHeader.getValue();
            assertTrue(headerVal.contains("partitionedCookie=partVal"), "Got: " + headerVal);
            assertTrue(headerVal.contains("SameSite=None"), "Got: " + headerVal);
            assertTrue(headerVal.contains("Partitioned"), "Got: " + headerVal);
            return null;
        });
    }

    private void httpPost(String relativePath) {
        HttpPost request = new HttpPost(DEFAULT_HOST_URL + relativePath);
        try {
            BasicHttpClientResponseHandler responseHandler = new BasicHttpClientResponseHandler();
            String response = httpClient.execute(request, responseHandler);
            assertNotNull(response);
        } catch (Exception ex) {
            fail(ex.toString());
        }
    }
}
