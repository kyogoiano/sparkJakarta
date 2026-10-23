package sparkTest.embeddedserver.jetty;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.SocketConnectorFactory;
import spark.ssl.SslStores;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class SocketConnectorFactoryTest {


    @Test
    public void testCreateSocketConnector() {

        final String host = "localhost";
        final int port = 8888;

        Server server = new Server();
        ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 8888, null, true);

        String internalHost = serverConnector.getHost();
        int internalPort = serverConnector.getPort();
        Server internalServerConnector = serverConnector.getServer();

        assertEquals(host, internalHost, "Server Connector Host should be set to the specified server");
        assertEquals(port, internalPort, "Server Connector Port should be set to the specified port");
        assertEquals(internalServerConnector, server, "Server Connector Server should be set to the specified server");
    }


    @Test
    public void testCreateSocketConnector_whenSslStoresIsNull() {

        Server server = new Server();

        try(final ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 80, null, true)) {
            if(serverConnector.getConnectionFactory(SslConnectionFactory.class) != null){
                fail("SocketConnector creation should not have an SSL connection factory");
            }
        } catch(IllegalArgumentException ex) {
            assertEquals("'sslStores' must not be null", ex.getMessage());
        }
    }


    @Test
    public void testCreateSecureSocketConnector() throws  Exception {

        final String host = "localhost";
        final int port = 8888;

        final String keystoreFile = "src/test/resources/keystore.jks";
        final String keystorePassword = "keyStorePassword";
        final String truststoreFile = "trustStoreFile.jks";
        final String trustStorePassword = "trustStorePassword";

        SslStores sslStores = SslStores.create(new URI(keystoreFile), keystorePassword, null, trustStorePassword);

        Server server = new Server();

        ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(server, host, port, sslStores, true);

        String internalHost = serverConnector.getHost();
        int internalPort = serverConnector.getPort();

        assertEquals(host, internalHost, "Server Connector Host should be set to the specified server");
        assertEquals(port, internalPort, "Server Connector Port should be set to the specified port");

        List<ConnectionFactory> factories = serverConnector.getConnectionFactories().stream().toList();

        Optional<SslConnectionFactory> OptSslConnectionFactory =
            factories.stream().filter(connectionFactory -> connectionFactory instanceof SslConnectionFactory).
                findFirst().map( connectionFactory -> (SslConnectionFactory) connectionFactory);
        assertTrue(OptSslConnectionFactory.isPresent(),
            "Should return true because factory for SSL should have been set");

        SslConnectionFactory sslConnectionFactory =  OptSslConnectionFactory.get();
        SslContextFactory sslContextFactory = sslConnectionFactory.getSslContextFactory();

        assertEquals("keystore.jks",
                sslContextFactory.getKeyStoreResource().getFileName(), "Should return the Keystore file specified");

    }

    @Test
    public void testCreateSocketConnector_trustForwardHeadersTrueVsFalse() {
        Server server = new Server();

        try (ServerConnector trustedConnector =
                 SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 8889, null, true);
             ServerConnector untrustedConnector =
                 SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 8890, null, false)) {

            org.eclipse.jetty.server.HttpConnectionFactory trustedHttpFactory =
                trustedConnector.getConnectionFactory(org.eclipse.jetty.server.HttpConnectionFactory.class);
            assertNotNull(trustedHttpFactory);
            assertEquals("https", trustedHttpFactory.getHttpConfiguration().getSecureScheme());
            assertTrue(
                trustedHttpFactory.getHttpConfiguration().getCustomizers().stream()
                    .anyMatch(c -> c instanceof org.eclipse.jetty.server.ForwardedRequestCustomizer),
                "ForwardedRequestCustomizer should be added when trustForwardHeaders is true"
            );
            assertEquals(3600000L, trustedConnector.getIdleTimeout());

            org.eclipse.jetty.server.HttpConnectionFactory untrustedHttpFactory =
                untrustedConnector.getConnectionFactory(org.eclipse.jetty.server.HttpConnectionFactory.class);
            assertNotNull(untrustedHttpFactory);
            assertFalse(
                untrustedHttpFactory.getHttpConfiguration().getCustomizers().stream()
                    .anyMatch(c -> c instanceof org.eclipse.jetty.server.ForwardedRequestCustomizer),
                "ForwardedRequestCustomizer should not be added when trustForwardHeaders is false"
            );
        }
    }

    @Test
    public void testCreateSecureSocketConnector_withFullSslStoresConfiguration() throws Exception {
        final String keystoreFile = "src/test/resources/keystore.jks";
        SslStores sslStores = SslStores.create(
            new URI(keystoreFile),
            "password",
            "JKS",
            "my-cert-alias",
            new URI(keystoreFile),
            "password",
            "JKS",
            true
        );

        Server server = new Server();
        try (ServerConnector serverConnector =
                 SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 8891, sslStores, false)) {

            SslConnectionFactory sslConnectionFactory =
                serverConnector.getConnectionFactory(SslConnectionFactory.class);
            assertNotNull(sslConnectionFactory);

            SslContextFactory.Server sslContextFactory = sslConnectionFactory.getSslContextFactory();
            assertEquals("JKS", sslContextFactory.getKeyStoreType());
            assertEquals("JKS", sslContextFactory.getTrustStoreType());
            assertEquals("my-cert-alias", sslContextFactory.getCertAlias());
            assertEquals("keystore.jks", sslContextFactory.getTrustStoreResource().getFileName());
            assertTrue(sslContextFactory.getNeedClientAuth());
            assertTrue(sslContextFactory.getWantClientAuth());
        }
    }

    @Test
    public void testCreateHttp2Connector_validation() {
        Server server = new Server();

        assertThrows(IllegalArgumentException.class,
            () -> SocketConnectorFactory.createHttp2Connector(
                null, "localhost", 8443, null, true));

        assertThrows(IllegalArgumentException.class,
            () -> SocketConnectorFactory.createHttp2Connector(
                server, null, 8443, null, true));

        assertThrows(IllegalArgumentException.class,
            () -> SocketConnectorFactory.createHttp2Connector(
                server, "localhost", 8443, null, true));
    }

    @Test
    public void testCreateHttp2Connector_configuresHttp2AndHttp11() throws Exception {
        final String keystoreFile = "src/test/resources/keystore.jks";
        SslStores sslStores = SslStores.create(
            new URI(keystoreFile), "password", null, "password");

        Server server = new Server();

        try (ServerConnector connector =
                 SocketConnectorFactory.createHttp2Connector(
                     server, "localhost", 8443, sslStores, true)) {

            assertEquals("localhost", connector.getHost());
            assertEquals(8443, connector.getPort());
            assertEquals(3600000L, connector.getIdleTimeout());

            HTTP2ServerConnectionFactory h2Factory =
                connector.getConnectionFactory(
                    HTTP2ServerConnectionFactory.class);

            assertNotNull(h2Factory,
                "HTTP/2 connection factory should be registered");

            HttpConnectionFactory http11Factory =
                connector.getConnectionFactory(HttpConnectionFactory.class);

            assertNotNull(http11Factory,
                "HTTP/1.1 fallback factory should be registered");

            ALPNServerConnectionFactory alpnFactory =
                connector.getConnectionFactory(
                    ALPNServerConnectionFactory.class);

            assertNotNull(alpnFactory,
                "ALPN connection factory should be registered");

            assertEquals(
                http11Factory.getProtocol(),
                alpnFactory.getDefaultProtocol(),
                "ALPN should fall back to HTTP/1.1");

            assertEquals("https",
                h2Factory.getHttpConfiguration().getSecureScheme());

            assertTrue(
                h2Factory.getHttpConfiguration().getCustomizers().stream()
                    .anyMatch(c -> c instanceof ForwardedRequestCustomizer),
                "ForwardedRequestCustomizer should be configured when trustForwardHeaders is true");
        }
    }

}
