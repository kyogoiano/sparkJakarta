package spark.util;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.*;

public class SparkTestUtil {

    private final int port;

    private CloseableHttpAsyncClient httpClient;

    public SparkTestUtil(int port) {
        this.port = port;
        this.httpClient = httpClientBuilder().build();
    }

    private HttpAsyncClientBuilder httpClientBuilder() {
//        SSLConnectionSocketFactory sslConnectionSocketFactory =
//            new SSLConnectionSocketFactory(Objects.requireNonNull(getSslFactory()), (paramString, paramSSLSession) -> true);
//
//        Registry<ConnectionSocketFactory> socketRegistry = RegistryBuilder
//            .<ConnectionSocketFactory>create()
//            .register("http", PlainConnectionSocketFactory.INSTANCE)
//            .register("https", sslConnectionSocketFactory)
//            .build();

        final PoolingAsyncClientConnectionManager connectionManager =
            PoolingAsyncClientConnectionManagerBuilder.create()
            .setTlsStrategy(ClientTlsStrategyBuilder.create()
                .setSslContext(getSslContext())
                .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                .build())
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
            .setConnPoolPolicy(PoolReusePolicy.LIFO)
            .setConnectionTimeToLive(TimeValue.ofMinutes(1L))
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(50))
                .setResponseTimeout(Timeout.ofSeconds(50))
                .setCookieSpec(StandardCookieSpec.STRICT)
                .build();
        return HttpAsyncClientBuilder.create().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig).setVersionPolicy(HttpVersionPolicy.NEGOTIATE);
    }

    public void setFollowRedirectStrategy(Integer... codes) {
        final List<Integer> redirectCodes = Arrays.asList(codes);
        DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
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
                                String acceptType, Map<String, String> reqHeaders) throws IOException, ParseException {
        final AsyncRequestProducer httpRequest = getHttpRequest(requestMethod, path, body, secureConnection, acceptType, reqHeaders);
        httpClient.start();

        final UrlResponse httpResponse = httpClient.execute(httpRequest, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse result) {

            }

            @Override
            public void failed(Exception ex) {

            }

            @Override
            public void cancelled() {

            }
        });
//                if (callback.getCode() >= 400) {
//                    throw new ClientProtocolException(Objects.toString(callback.getCode()));
//                }
                final UrlResponse urlResponse = new UrlResponse();
                urlResponse.status = callback.getCode();
                final HttpEntity entity = callback.getEntity();
                if (entity != null) {
                    urlResponse.body = EntityUtils.toString(entity);
                } else {
                    urlResponse.body = "";
                }
                final Map<String, String> headers = new HashMap<>();
                final Header[] allHeaders = callback.getHeaders();
                for (final Header header : allHeaders) {
                    headers.put(header.getName(), header.getValue());
                }
                urlResponse.headers = headers;
                return urlResponse;
            });

        return httpResponse;
    }

    private AsyncRequestProducer getHttpRequest(String requestMethod, String path, String body, boolean secureConnection,
                                                String acceptType, Map<String, String> reqHeaders) {

        String protocol = secureConnection ? "https" : "http";
        String uri = protocol + "://localhost:" + port + path;

        final AsyncRequestBuilder asyncRequestBuilder = AsyncRequestBuilder.create(requestMethod).setUri(uri);
        switch (requestMethod) {
             case "GET", "DELETE":
                 asyncRequestBuilder.setHeader("Accept", acceptType);
             break;
             case "POST", "PATCH", "PUT":
                 asyncRequestBuilder.setHeader("Accept", acceptType);
                         asyncRequestBuilder.setEntity(body);
             break;
            case "HEAD", "TRACE", "OPTIONS", "LOCK":
            default: throw new IllegalArgumentException("Unknown method " + requestMethod);
        }

        addHeaders(reqHeaders, asyncRequestBuilder);

        return asyncRequestBuilder.build();
    }

    private void addHeaders(final Map<String, String> reqHeaders, final AsyncRequestBuilder req) {
        if (reqHeaders != null) {
            for (final Map.Entry<String, String> header : reqHeaders.entrySet()) {
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
    private SSLContext getSslContext() {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream fis = new FileInputStream(getTrustStoreLocation());
            keyStore.load(fis, getTrustStorePassword().toCharArray());
            fis.close();

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
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
