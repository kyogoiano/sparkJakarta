package spark.util;

import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SparkTestUtil {

    private int port;

    private CloseableHttpClient httpClient;

    public SparkTestUtil(int port) {
        this.port = port;
        this.httpClient = httpClientBuilder().build();
    }

    private HttpClientBuilder httpClientBuilder() {
        SSLConnectionSocketFactory sslConnectionSocketFactory =
                new SSLConnectionSocketFactory(getSslFactory(), (paramString, paramSSLSession) -> true);
        Registry<ConnectionSocketFactory> socketRegistry = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", sslConnectionSocketFactory)
                .build();
        BasicHttpClientConnectionManager connManager = new BasicHttpClientConnectionManager(socketRegistry);
        return HttpClientBuilder.create().setConnectionManager(connManager);
    }

    public void setFollowRedirectStrategy(Integer... codes) {
        final List<Integer> redirectCodes = Arrays.asList(codes);
        DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
            public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                boolean isRedirect = false;
                try {
                    isRedirect = super.isRedirected(request, response, context);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!isRedirect) {
                    int responseCode = response.getCode();
                    if (redirectCodes.contains(responseCode)) {
                        return true;
                    }
                }
                return isRedirect;
            }
        };
        this.httpClient = httpClientBuilder().setRedirectStrategy(redirectStrategy).build();
    }

    public UrlResponse get(String path) throws Exception {
        return doMethod("GET", path, null);
    }


    public UrlResponse doMethodSecure(String requestMethod, String path, String body)
            throws Exception {
        return doMethod(requestMethod, path, body, true, "text/html");
    }

    public UrlResponse doMethod(String requestMethod, String path, String body) throws Exception {
        return doMethod(requestMethod, path, body, false, "text/html");
    }

    public UrlResponse doMethodSecure(String requestMethod, String path, String body, String acceptType)
            throws Exception {
        return doMethod(requestMethod, path, body, true, acceptType);
    }

    public UrlResponse doMethod(String requestMethod, String path, String body, String acceptType) throws Exception {
        return doMethod(requestMethod, path, body, false, acceptType);
    }

    private UrlResponse doMethod(String requestMethod, String path, String body, boolean secureConnection,
                                 String acceptType) throws Exception {
        return doMethod(requestMethod, path, body, secureConnection, acceptType, null);
    }

    public UrlResponse doMethod(String requestMethod, String path, String body, boolean secureConnection,
                                String acceptType, Map<String, String> reqHeaders) throws IOException, ParseException {
        HttpUriRequest httpRequest = getHttpRequest(requestMethod, path, body, secureConnection, acceptType, reqHeaders);
        CloseableHttpResponse httpResponse = httpClient.execute(httpRequest);

        UrlResponse urlResponse = new UrlResponse();
        urlResponse.status = httpResponse.getCode();
        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            urlResponse.body = EntityUtils.toString(entity);
        } else {
            urlResponse.body = "";
        }
        Map<String, String> headers = new HashMap<>();
        Header[] allHeaders = httpResponse.getHeaders();
        for (Header header : allHeaders) {
            headers.put(header.getName(), header.getValue());
        }
        urlResponse.headers = headers;
        return urlResponse;
    }

    private HttpUriRequest getHttpRequest(String requestMethod, String path, String body, boolean secureConnection,
                                          String acceptType, Map<String, String> reqHeaders) {

        String protocol = secureConnection ? "https" : "http";
        String uri = protocol + "://localhost:" + port + path;

        if (requestMethod.equals("GET")) {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.setHeader("Accept", acceptType);
            addHeaders(reqHeaders, httpGet);
            return httpGet;
        }

        if (requestMethod.equals("POST")) {
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader("Accept", acceptType);
            addHeaders(reqHeaders, httpPost);
            httpPost.setEntity(new StringEntity(body));
            return httpPost;
        }

        if (requestMethod.equals("PATCH")) {
            HttpPatch httpPatch = new HttpPatch(uri);
            httpPatch.setHeader("Accept", acceptType);
            addHeaders(reqHeaders, httpPatch);
            httpPatch.setEntity(new StringEntity(body));
            return httpPatch;
        }

        if (requestMethod.equals("DELETE")) {
            HttpDelete httpDelete = new HttpDelete(uri);
            addHeaders(reqHeaders, httpDelete);
            httpDelete.setHeader("Accept", acceptType);
            return httpDelete;
        }

        if (requestMethod.equals("PUT")) {
            HttpPut httpPut = new HttpPut(uri);
            httpPut.setHeader("Accept", acceptType);
            addHeaders(reqHeaders, httpPut);
            httpPut.setEntity(new StringEntity(body));
            return httpPut;
        }

        if (requestMethod.equals("HEAD")) {
            HttpHead httpHead = new HttpHead(uri);
            addHeaders(reqHeaders, httpHead);
            return httpHead;
        }

        if (requestMethod.equals("TRACE")) {
            HttpTrace httpTrace = new HttpTrace(uri);
            addHeaders(reqHeaders, httpTrace);
            return httpTrace;
        }

        if (requestMethod.equals("OPTIONS")) {
            HttpOptions httpOptions = new HttpOptions(uri);
            addHeaders(reqHeaders, httpOptions);
            return httpOptions;
        }

        if (requestMethod.equals("LOCK")) {
            HttpLock httpLock = new HttpLock(uri);
            addHeaders(reqHeaders, httpLock);
            return httpLock;
        }

        throw new IllegalArgumentException("Unknown method " + requestMethod);

    }

    private void addHeaders(Map<String, String> reqHeaders, HttpRequest req) {
        if (reqHeaders != null) {
            for (Map.Entry<String, String> header : reqHeaders.entrySet()) {
                req.addHeader(header.getKey(), header.getValue());
            }
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * Convenience method to use own truststore on SSL Sockets. Will default to
     * the self signed keystore provided in resources, but will respect
     * <p/>
     * -Djavax.net.ssl.keyStore=serverKeys
     * -Djavax.net.ssl.keyStorePassword=password
     * -Djavax.net.ssl.trustStore=serverTrust
     * -Djavax.net.ssl.trustStorePassword=password SSLApplication
     * <p/>
     * So these can be used to specify other key/trust stores if required.
     *
     * @return an SSL Socket Factory using either provided keystore OR the
     * keystore specified in JVM params
     */
    private SSLSocketFactory getSslFactory() {
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream fis = new FileInputStream(getTrustStoreLocation());
            keyStore.load(fis, getTrustStorePassword().toCharArray());
            fis.close();

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Return JVM param set keystore or default if not set.
     *
     * @return Keystore location as string
     */
    public static String getKeyStoreLocation() {
        String keyStoreLoc = System.getProperty("javax.net.ssl.keyStore");
        return keyStoreLoc == null ? "./src/test/resources/keystore.jks" : keyStoreLoc;
    }

    /**
     * Return JVM param set keystore password or default if not set.
     *
     * @return Keystore password as string
     */
    public static String getKeystorePassword() {
        String password = System.getProperty("javax.net.ssl.keyStorePassword");
        return password == null ? "password" : password;
    }

    /**
     * Return JVM param set truststore location, or keystore location if not
     * set. if keystore not set either, returns default
     *
     * @return truststore location as string
     */
    public static String getTrustStoreLocation() {
        String trustStoreLoc = System.getProperty("javax.net.ssl.trustStore");
        return trustStoreLoc == null ? getKeyStoreLocation() : trustStoreLoc;
    }

    /**
     * Return JVM param set truststore password or keystore password if not set.
     * If still not set, will return default password
     *
     * @return truststore password as string
     */
    public static String getTrustStorePassword() {
        String password = System.getProperty("javax.net.ssl.trustStorePassword");
        return password == null ? getKeystorePassword() : password;
    }

    public static class UrlResponse {

        public Map<String, String> headers;
        public String body;
        public int status;
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
        }
    }

    static class HttpLock extends HttpUriRequestBase {
        public final static String METHOD_NAME = "LOCK";

        public HttpLock(final String uri) {
            super(METHOD_NAME, URI.create(uri));
        }

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }
    }

}
