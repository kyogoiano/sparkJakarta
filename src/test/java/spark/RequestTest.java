package spark;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.routematch.RouteMatch;
import spark.util.SparkTestUtil;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static spark.Spark.*;

import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

    private static final String THE_SERVLET_PATH = "/the/servlet/path";
    private static final String THE_CONTEXT_PATH = "/the/context/path";
    private static final String THE_MATCHED_ROUTE = "/users/:username";
    private static final String BEFORE_MATCHED_ROUTE = "/users/:before";
    private static final String AFTER_MATCHED_ROUTE = "/users/:after";
    private static final String AFTERAFTER_MATCHED_ROUTE = "/users/:afterafter";

    private static SparkTestUtil http;

    HttpServletRequest servletRequest;
    HttpSession httpSession;
    Request request;

    RouteMatch match = new RouteMatch(null, "/hi", "/hi", "text/html", null);
    RouteMatch matchWithParams = new RouteMatch(null, "/users/:username", "/users/bob", "text/html", null);

    @BeforeEach
    public void setup() {
        http = new SparkTestUtil(4567);

        before(BEFORE_MATCHED_ROUTE, (q, a) -> {
            System.out.println("before filter matched");
            shouldBeAbleToGetTheMatchedPathInBeforeFilter(q);
        });
        get(THE_MATCHED_ROUTE, (q,a)-> "Get filter matched");
        after(AFTER_MATCHED_ROUTE, (q, a) -> {
            System.out.println("after filter matched");
            shouldBeAbleToGetTheMatchedPathInAfterFilter(q);
        });
        afterAfter(AFTERAFTER_MATCHED_ROUTE, (q, a) -> {
            System.out.println("afterafter filter matched");
            shouldBeAbleToGetTheMatchedPathInAfterAfterFilter(q);
        });

        awaitInitialization();


        servletRequest = mock(HttpServletRequest.class);
        httpSession = mock(HttpSession.class);

        request = new Request(match, servletRequest);

    }

    @Test
    public void queryParamShouldReturnsParametersFromQueryString() {

        when(servletRequest.getParameter("name")).thenReturn("Federico");

        String name = request.queryParams("name");
        assertEquals("Federico", name, "Invalid name in query string");
    }

    @Test
    public void queryParamOrDefault_shouldReturnQueryParam_whenQueryParamExists() {

        when(servletRequest.getParameter("name")).thenReturn("Federico");

        String name = request.queryParamOrDefault("name", "David");
        assertEquals("Federico", name, "Invalid name in query string");
    }

    @Test
    public void queryParamOrDefault_shouldReturnDefault_whenQueryParamIsNull() {

        when(servletRequest.getParameter("name")).thenReturn(null);

        String name = request.queryParamOrDefault("name", "David");
        assertEquals("David", name, "Invalid name in default value");
    }

    @Test
    public void queryParamShouldBeParsedAsHashMap() {
        Map<String, String[]> params = new HashMap<>();
        params.put("user[name]", new String[] {"Federico"});

        when(servletRequest.getParameterMap()).thenReturn(params);

        String name = request.queryMap("user").value("name");
        assertEquals( "Federico", name, "Invalid name in query string");
    }

    @Test
    public void shouldBeAbleToGetTheServletPath() {

        when(servletRequest.getServletPath()).thenReturn(THE_SERVLET_PATH);

        Request request = new Request(match, servletRequest);
        assertEquals(THE_SERVLET_PATH, request.servletPath(), "Should have delegated getting the servlet path");
    }

    @Test
    public void shouldBeAbleToGetTheContextPath() {

        when(servletRequest.getContextPath()).thenReturn(THE_CONTEXT_PATH);

        Request request = new Request(match, servletRequest);
        assertEquals(THE_CONTEXT_PATH, request.contextPath(), "Should have delegated getting the context path");
    }

    @Test
    public void shouldBeAbleToGetTheMatchedPath() {
        Request request = new Request(matchWithParams, servletRequest);
        assertEquals(THE_MATCHED_ROUTE, request.matchedPath(), "Should have returned the matched route");
        try {
            http.get("/users/bob");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shouldBeAbleToGetTheMatchedPathInBeforeFilter(Request q) {
        assertEquals(BEFORE_MATCHED_ROUTE, q.matchedPath(), "Should have returned the matched route from the before filter");
    }

    public void shouldBeAbleToGetTheMatchedPathInAfterFilter(Request q) {
        assertEquals(AFTER_MATCHED_ROUTE, q.matchedPath(), "Should have returned the matched route from the after filter");
    }

    public void shouldBeAbleToGetTheMatchedPathInAfterAfterFilter(Request q) {
        assertEquals(AFTERAFTER_MATCHED_ROUTE, q.matchedPath(), "Should have returned the matched route from the afterafter filter");
    }

    /**
     * "A Session with an HTTPSession from the Request should have been created"
     */
    @Test
    public void testSessionNoParams_whenSessionIsNull() {

        when(servletRequest.getSession()).thenReturn(httpSession);

        assertEquals(
                httpSession, request.session().raw());
    }

    /**
     * "A Session with an HTTPSession from the Request should have been created because create parameter " +
     *                         "was set to true"
     */
    @Test
    public void testSession_whenCreateIsTrue() {

        when(servletRequest.getSession(true)).thenReturn(httpSession);

        assertEquals(
                httpSession, request.session(true).raw());

    }

    /**
     * "A Session should not have been created because create parameter was set to false"
     */
    @Test
    public void testSession_whenCreateIsFalse() {

        when(servletRequest.getSession(true)).thenReturn(httpSession);

        assertNull(request.session(false));

    }


    @Test
    public void testSessionNpParams_afterSessionInvalidate() {
        when(servletRequest.getSession()).thenReturn(httpSession);

        Session session = request.session();
        session.invalidate();
        request.session();

        verify(servletRequest, times(2)).getSession();
    }

    @Test
    public void testSession_whenCreateIsTrue_afterSessionInvalidate() {
        when(servletRequest.getSession(true)).thenReturn(httpSession);

        Session session = request.session(true);
        session.invalidate();
        request.session(true);

        verify(servletRequest, times(2)).getSession(true);
    }

    @Test
    public void testSession_whenCreateIsFalse_afterSessionInvalidate() {
        when(servletRequest.getSession()).thenReturn(httpSession);
        when(servletRequest.getSession(false)).thenReturn(null);

        Session session = request.session();
        session.invalidate();
        request.session(false);

        verify(servletRequest, times(1)).getSession(false);
    }

    @Test
    public void testSession_2times() {
        when(servletRequest.getSession(true)).thenReturn(httpSession);

        Session session = request.session(true);
        session = request.session(true);

        assertNotNull(session);
        verify(servletRequest, times(1)).getSession(true);
    }

    @Test
    public void testCookies_whenCookiesArePresent() {

        Collection<Cookie> cookies = new ArrayList<>();
        cookies.add(new Cookie("cookie1", "cookie1value"));
        cookies.add(new Cookie("cookie2", "cookie2value"));

        Map<String, String> expected = new HashMap<>();
        for(Cookie cookie : cookies) {
            expected.put(cookie.getName(), cookie.getValue());
        }

        Cookie[] cookieArray = cookies.toArray(new Cookie[0]);

        when(servletRequest.getCookies()).thenReturn(cookieArray);

        //"The count of cookies returned should be the same as those in the request"
        assertEquals(2, request.cookies().size());

        //"A Map of Cookies should have been returned because they exist"
        assertEquals(expected, request.cookies());

    }

    @Test
    public void testCookies_whenCookiesAreNotPresent() {

        when(servletRequest.getCookies()).thenReturn(null);

        //"A Map of Cookies should have been instantiated even if cookies are not present in the request",
        assertNotNull(request.cookies());

        //"The Map of cookies should be empty because cookies are not present in the request"
        assertEquals(0, request.cookies().size());

    }

    @Test
    public void testCookie_whenCookiesArePresent() {

        final String cookieKey = "cookie1";
        final String cookieValue = "cookie1value";

        Collection<Cookie> cookies = new ArrayList<>();
        cookies.add(new Cookie(cookieKey, cookieValue));

        Cookie[] cookieArray = cookies.toArray(new Cookie[0]);
        when(servletRequest.getCookies()).thenReturn(cookieArray);

        assertNotNull(
                request.cookie(cookieKey), "A value for the key provided should exist because a cookie with the same key is present");

        assertEquals(
                cookieValue, request.cookie(cookieKey), "The correct value for the cookie key supplied should be returned");

    }

    @Test
    public void testCookie_whenCookiesAreNotPresent() {

        final String cookieKey = "nonExistentCookie";

        when(servletRequest.getCookies()).thenReturn(null);

        assertNull(
                request.cookie(cookieKey), "A null value should have been returned because the cookie with that key does not exist");

    }

    @Test
    public void testRequestMethod() {

        final String requestMethod = "GET";

        when(servletRequest.getMethod()).thenReturn(requestMethod);

        assertEquals(
                requestMethod, request.requestMethod(), "The request method of the underlying servlet request should be returned");

    }

    @Test
    public void testScheme() {

        final String scheme = "http";

        when(servletRequest.getScheme()).thenReturn(scheme);

        assertEquals(
                scheme, request.scheme(), "The scheme of the underlying servlet request should be returned");

    }

    @Test
    public void testHost() {

        final String host = "www.google.com";

        when(servletRequest.getHeader("host")).thenReturn(host);

        assertEquals(
                host, request.host(), "The value of the host header of the underlying servlet request should be returned");

    }

    @Test
    public void testUserAgent() {

        final String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36";

        when(servletRequest.getHeader("user-agent")).thenReturn(userAgent);

        assertEquals(userAgent, request.userAgent(), "The value of the user agent header of the underlying servlet request should be returned" );

    }

    @Test
    public void testPort() {

        final int port = 80;

        when(servletRequest.getServerPort()).thenReturn(80);

        assertEquals(
                port, request.port(), "The server port of the the underlying servlet request should be returned");

    }

    @Test
    public void testPathInfo() {

        final String pathInfo = "/path/to/resource";

        when(servletRequest.getPathInfo()).thenReturn(pathInfo);

        assertEquals(
                pathInfo, request.pathInfo(), "The path info of the underlying servlet request should be returned");

    }

    @Test
    public void testServletPath() {

        final String servletPath = "/api";

        when(servletRequest.getServletPath()).thenReturn(servletPath);

        assertEquals(
                servletPath, request.servletPath(), "The servlet path of the underlying servlet request should be returned");

    }

    @Test
    public void testContextPath() {

        final String contextPath = "/my-app";

        when(servletRequest.getContextPath()).thenReturn(contextPath);

        assertEquals(
                contextPath, request.contextPath(), "The context path of the underlying servlet request should be returned");

    }

    @Test
    public void testUrl() {

        final String url = "http://www.myapp.com/myapp/a";

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer(url));

        assertEquals(
                url, request.url(), "The request url of the underlying servlet request should be returned");

    }

    @Test
    public void testContentType() {

        final String contentType = "image/jpeg";

        when(servletRequest.getContentType()).thenReturn(contentType);

        assertEquals(
                contentType, request.contentType(), "The content type of the underlying servlet request should be returned");

    }

    @Test
    public void testIp() {

        final String ip = "216.58.197.106:80";

        when(servletRequest.getRemoteAddr()).thenReturn(ip);

        assertEquals(
                ip, request.ip(), "The remote IP of the underlying servlet request should be returned");

    }

    @Test
    public void testContentLength() {

        final int contentLength = 500;

        when(servletRequest.getContentLength()).thenReturn(contentLength);

        assertEquals(
                contentLength, request.contentLength(), "The content length the underlying servlet request should be returned");

    }

    @Test
    public void testHeaders() {

        final String headerKey = "host";
        final String host = "www.google.com";

        when(servletRequest.getHeader(headerKey)).thenReturn(host);

        assertEquals(
                host, request.headers(headerKey), "The value of the header specified should be returned");

    }

    /**
     * "An array of Strings for a parameter with multiple values should be returned"
     */
    @Test
    public void testQueryParamsValues_whenParamExists() {

        final String[] paramValues = {"foo", "bar"};

        when(servletRequest.getParameterValues("id")).thenReturn(paramValues);

        assertArrayEquals(
                paramValues, request.queryParamsValues("id"));

    }

    @Test
    public void testQueryParamsValues_whenParamDoesNotExists() {

        when(servletRequest.getParameterValues("id")).thenReturn(null);

        assertNull(
                request.queryParamsValues("id"), "Null should be returned because the parameter specified does not exist in the request");

    }

    @Test
    public void testQueryParams() {

        Map<String, String[]> params = new HashMap<>();
        params.put("sort", new String[]{"asc"});
        params.put("items", new String[]{"10"});

        when(servletRequest.getParameterMap()).thenReturn(params);

        Set<String> result = request.queryParams();

        assertArrayEquals(params.keySet().toArray(), result.toArray(), "Should return the query parameter names");

    }

    @Test
    public void testURI() {

        final String requestURI = "http://localhost:8080/myapp/";

        when(servletRequest.getRequestURI()).thenReturn(requestURI);

        assertEquals(
                requestURI, request.uri(), "The request URI should be returned");

    }

    @Test
    public void testProtocol() {

        final String protocol = "HTTP/1.1";

        when(servletRequest.getProtocol()).thenReturn(protocol);

        assertEquals(
                protocol, request.protocol(), "The underlying request protocol should be returned");

    }
}
