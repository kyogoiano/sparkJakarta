package spark.util;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.AbstractRequestBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SparkTestUtil {

    private final int port;

    private CloseableHttpClient httpClient;

    public SparkTestUtil(int port) {
        this.port = port;

        this.httpClient = httpClientBuilder().setConnectionManagerShared(true).build();
    }

//    public void closeClient() throws IOException {
//        this.httpClient.close();
//    }

    private HttpClientBuilder httpClientBuilder() {
        final SSLConnectionSocketFactory sslConnectionSocketFactory =
                new SSLConnectionSocketFactory(Objects.requireNonNull(getSslFactory()), (paramString, paramSSLSession) -> true);
        final Registry<ConnectionSocketFactory> socketRegistry = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", sslConnectionSocketFactory)
                .build();
        final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketRegistry);
        connManager.setDefaultMaxPerRoute(5);
        connManager.setMaxTotal(5);
        connManager.setDefaultSocketConfig(SocketConfig.custom().
            setSoTimeout(Timeout.of(5000L, TimeUnit.MILLISECONDS)).build());
        connManager.closeIdle(TimeValue.ofMilliseconds(0L));
        connManager.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);
        return HttpClientBuilder.create().setConnectionManager(connManager);
    }

    public void setFollowRedirectStrategy(final Integer... codes) {
        final List<Integer> redirectCodes = Arrays.asList(codes);
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
            @Override
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
                                String acceptType, final Map<String, String> reqHeaders) throws IOException, ParseException {
        final ClassicHttpRequest httpRequest = getHttpRequest(requestMethod, path, body, secureConnection, acceptType, reqHeaders);

        try(final CloseableHttpResponse httpResponse = httpClient.execute(httpRequest)) {
            final UrlResponse urlResponse = new UrlResponse();
            urlResponse.status = httpResponse.getCode();
            final HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                urlResponse.body = EntityUtils.toString(entity);
            } else {
                urlResponse.body = "";
            }
            EntityUtils.consume(entity);
            final Map<String, String> headers;
            final Header[] allHeaders = httpResponse.getHeaders();
            headers = Arrays.stream(allHeaders).
                collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue, (a, b) -> b));
            urlResponse.headers = headers;
            return urlResponse;
        }
    }

    private ClassicHttpRequest getHttpRequest(final String requestMethod, final String path, final String body,
                                              final boolean secureConnection, final String acceptType, final Map<String, String> reqHeaders) {

        final String protocol = secureConnection ? "https" : "http";
        final String uri = protocol + "://localhost:" + port + path;

        final ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(requestMethod).setUri(uri);
        switch (requestMethod) {
            case "GET", "DELETE" -> requestBuilder.setHeader("Accept", acceptType);
            case "POST", "PATCH", "PUT" -> {
                requestBuilder.setHeader("Accept", acceptType);
                requestBuilder.setEntity(body);
            }
            case "HEAD", "TRACE", "OPTIONS", "LOCK" -> {
            }
            default -> throw new IllegalArgumentException("Unknown method " + requestMethod);
        }

        addHeaders(reqHeaders, requestBuilder);

        return requestBuilder.build();
    }

    private void addHeaders(final Map<String, String> reqHeaders, final AbstractRequestBuilder<? extends ClassicHttpRequest> requestBuilder) {
        if (reqHeaders != null) {
            reqHeaders.forEach(requestBuilder::addHeader);
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
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            final FileInputStream fis = new FileInputStream(getTrustStoreLocation());
            keyStore.load(fis, getTrustStorePassword().toCharArray());
            fis.close();

            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            final SSLContext ctx = SSLContext.getInstance("TLS");
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
        } catch (Exception ignored) {
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
