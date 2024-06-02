package sparkTest.embeddedserver.jetty;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
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
    public void testCreateSocketConnector_whenServerIsNull_thenThrowException() {

        try(final ServerConnector serverConnector =
                SocketConnectorFactory.createSecureSocketConnector(null, "host",  80, null,true)) {
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'server' must not be null", ex.getMessage());
        }
    }


    @Test
    public void testCreateSocketConnector_whenHostIsNull_thenThrowException() {

        Server server = new Server();

        try (final ServerConnector serverConnector =
                 SocketConnectorFactory.createSecureSocketConnector(server, null, 80, null, true)){
             fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'host' must not be null", ex.getMessage());
        }
    }

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
    public void testCreateSecureSocketConnector_whenServerIsNull() {

        try (final ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(null, "localhost", 80, null, true)){
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'server' must not be null", ex.getMessage());
        }
    }

    @Test
    public void testCreateSecureSocketConnector_whenHostIsNull() {

        Server server = new Server();

        try (final ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(server, null, 80, null, true)){
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'host' must not be null", ex.getMessage());
        }
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

}
