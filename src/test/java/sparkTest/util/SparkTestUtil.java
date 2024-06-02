package sparkTest.util;

import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SparkTestUtil {

    private final int port;

    private CloseableHttpAsyncClient httpClient;

    public SparkTestUtil(int port) {
        this.port = port;

        this.httpClient = httpClientBuilder().setConnectionManagerShared(true).build();
    }

    public void closeClient() throws IOException {
        this.httpClient.close();
    }

    private HttpAsyncClientBuilder httpClientBuilder() {
//        final SSLConnectionSocketFactory sslConnectionSocketFactory =
//                new SSLConnectionSocketFactory(Objects.requireNonNull(getSslFactory()), (paramString, paramSSLSession) -> true);
//        final Registry<ConnectionSocketFactory> socketRegistry = RegistryBuilder
//                .<ConnectionSocketFactory>create()
//                .register("http", PlainConnectionSocketFactory.INSTANCE)
//                .register("https", sslConnectionSocketFactory)
//                .build();


        final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create().setSslContext(getSslContext())
            .build();
        final PoolConcurrencyPolicy poolConcurrencyPolicy = PoolConcurrencyPolicy.LAX;
        final PoolingAsyncClientConnectionManager connManager =  PoolingAsyncClientConnectionManagerBuilder.create()
            .setTlsStrategy(tlsStrategy)
            .setPoolConcurrencyPolicy(poolConcurrencyPolicy)
            .setConnPoolPolicy(PoolReusePolicy.LIFO)
            .setMaxConnPerRoute(5)
            .setMaxConnTotal(5)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofMinutes(1))
                .setConnectTimeout(Timeout.ofMinutes(1))
                .setTimeToLive(TimeValue.ofMinutes(2)).setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND)
                .build()).build();

//        connManager.setDefaultMaxPerRoute(5);
//        connManager.setMaxTotal(5);
//        connManager.setDefaultSocketConfig(SocketConfig.custom().
//            setSoTimeout(Timeout.of(5000L, TimeUnit.MILLISECONDS)).build());
//        connManager.closeIdle(TimeValue.ofMilliseconds(0L));
//        connManager.setConnectionConfigResolver(httpRoute ->
//            ConnectionConfig.custom().setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND).build());
        return HttpAsyncClientBuilder.create().setConnectionManager(connManager).setIOReactorConfig(IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofMinutes(1))
            .build());
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
                                String acceptType, final Map<String, String> reqHeaders) throws IOException, ParseException, URISyntaxException, ExecutionException, InterruptedException {
        final AsyncRequestBuilder requestBuilder = getHttpRequest(requestMethod, path, body, secureConnection, acceptType, reqHeaders);
        final HttpHost httpHost = HttpHost.create(requestBuilder.getUri());

        httpClient.start();

        final AsyncRequestProducer request = requestBuilder
            .setHttpHost(httpHost)
            .setPath(path)
            .build();


        final HttpClientContext clientContext = HttpClientContext.create();

        final FutureCallback<SimpleHttpResponse> futureCallback = new FutureCallback<>() {

            @Override
            public void completed(final SimpleHttpResponse response) {
                System.out.println(request + "->" + new StatusLine(response));
                final SSLSession sslSession = clientContext.getSSLSession();
                if (sslSession != null) {
                    System.out.println("SSL protocol " + sslSession.getProtocol());
                    System.out.println("SSL cipher suite " + sslSession.getCipherSuite());
                }
                System.out.println(response.getBody());
            }

            @Override
            public void failed(final Exception ex) {
                final SSLSession sslSession = clientContext.getSSLSession();
                if (sslSession != null) {
                    System.out.println("SSL protocol " + sslSession.getProtocol());
                    System.out.println("SSL cipher suite " + sslSession.getCipherSuite());
                }
                System.out.println(request + "->" + ex);
            }

            @Override
            public void cancelled() {
                System.out.println(request + " cancelled");
            }

        };


        final Future<SimpleHttpResponse> future =
            httpClient.execute(request,
                SimpleResponseConsumer.create(), clientContext, futureCallback);

        final SimpleHttpResponse httpResponse = future.get();

        final UrlResponse urlResponse = new UrlResponse();
        urlResponse.status = httpResponse.getCode();

        SimpleBody simpleBody = httpResponse.getBody();
        if (simpleBody != null) {
            urlResponse.body = simpleBody.getBodyText();
        } else {
            urlResponse.body = "";
        }


        final Map<String, String> headers;
        final Header[] allHeaders = httpResponse.getHeaders();
        headers = Arrays.stream(allHeaders).
            collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue, (a, b) -> b));
        urlResponse.headers = headers;


        return urlResponse;

    }

    private AsyncRequestBuilder getHttpRequest(final String requestMethod, final String path, final String body,
                                                final boolean secureConnection, final String acceptType, final Map<String, String> reqHeaders) {

        final String protocol = secureConnection ? "https" : "http";
        final String uri = protocol + "://localhost:" + port + path;

        final AsyncRequestBuilder requestBuilder = AsyncRequestBuilder.create(requestMethod).setUri(uri);
        requestBuilder.setCharset(StandardCharsets.UTF_8);
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

        return requestBuilder;
    }

    private void addHeaders(final Map<String, String> reqHeaders, final AsyncRequestBuilder requestBuilder) {
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
            final FileInputStream fis = new FileInputStream(getTrustStoreLocation().getPath());
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
            final FileInputStream fis = new FileInputStream(getTrustStoreLocation().getPath());
            keyStore.load(fis, getTrustStorePassword().toCharArray());
            fis.close();

            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            //final SSLContext ctx = SSLContext.getInstance("TLS");
            //ctx.init(null, tmf.getTrustManagers(), null);

            SSLContext ctx = SSLContexts.custom().setSecureRandom(null).setKeyStoreType("TLS")
                .setTrustManagerFactoryAlgorithm(TrustManagerFactory.getDefaultAlgorithm())
                .build();
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
    public static URI getKeyStoreLocation() throws URISyntaxException {
        String keyStoreLoc = System.getProperty("javax.net.ssl.keyStore");
        return keyStoreLoc == null ? new URI("./src/test/resources/keystore.jks") : new URI(keyStoreLoc);
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
    public static URI getTrustStoreLocation() throws URISyntaxException {
        String trustStoreLoc = System.getProperty("javax.net.ssl.trustStore");
        return trustStoreLoc == null ? getKeyStoreLocation() : new URI(trustStoreLoc);
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
