package spark.embeddedserver.jetty;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.Whitebox;
import spark.ssl.SslStores;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class SocketConnectorFactoryTest {

    @Test
    public void testCreateSocketConnector_whenServerIsNull_thenThrowException() {

        try {
            SocketConnectorFactory.createSocketConnector(null, "host", 80, true);
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'server' must not be null", ex.getMessage());
        }
    }


    @Test
    public void testCreateSocketConnector_whenHostIsNull_thenThrowException() {

        Server server = new Server();

        try {
            SocketConnectorFactory.createSocketConnector(server, null, 80, true);
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
        ServerConnector serverConnector = SocketConnectorFactory.createSocketConnector(server, "localhost", 8888, true);

        String internalHost = Whitebox.getInternalState(serverConnector, "_host");
        int internalPort = Whitebox.getInternalState(serverConnector, "_port");
        Server internalServerConnector = Whitebox.getInternalState(serverConnector, "_server");

        assertEquals(host, internalHost, "Server Connector Host should be set to the specified server");
        assertEquals(port, internalPort, "Server Connector Port should be set to the specified port");
        assertEquals(internalServerConnector, server, "Server Connector Server should be set to the specified server");
    }

    @Test
    public void testCreateSecureSocketConnector_whenServerIsNull() {

        try {
            SocketConnectorFactory.createSecureSocketConnector(null, "localhost", 80, null, true);
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'server' must not be null", ex.getMessage());
        }
    }

    @Test
    public void testCreateSecureSocketConnector_whenHostIsNull() {

        Server server = new Server();

        try {
            SocketConnectorFactory.createSecureSocketConnector(server, null, 80, null, true);
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'host' must not be null", ex.getMessage());
        }
    }

    @Test
    public void testCreateSecureSocketConnector_whenSslStoresIsNull() {

        Server server = new Server();

        try {
            SocketConnectorFactory.createSecureSocketConnector(server, "localhost", 80, null, true);
            fail("SocketConnector creation should have thrown an IllegalArgumentException");
        } catch(IllegalArgumentException ex) {
            assertEquals("'sslStores' must not be null", ex.getMessage());
        }
    }


    @Test
    @PrepareForTest({ServerConnector.class})
    public void testCreateSecureSocketConnector() throws  Exception {

        final String host = "localhost";
        final int port = 8888;

        final String keystoreFile = "keystore.jks";
        final String keystorePassword = "keystorePassword";
        final String truststoreFile = "truststoreFile.jks";
        final String trustStorePassword = "trustStorePassword";

        final ClassLoader classLoader = getClass().getClassLoader();
        final File file = new File(Objects.requireNonNull(classLoader.getResource(keystoreFile)).getFile());
        final String keystorePath = file.getCanonicalPath();

        SslStores sslStores = SslStores.create(keystorePath, keystorePassword, truststoreFile, trustStorePassword);

        Server server = new Server();

        ServerConnector serverConnector = SocketConnectorFactory.createSecureSocketConnector(server, host, port, sslStores, true);

        String internalHost = Whitebox.getInternalState(serverConnector, "_host");
        int internalPort = Whitebox.getInternalState(serverConnector, "_port");

        assertEquals(host, internalHost, "Server Connector Host should be set to the specified server");
        assertEquals(port, internalPort, "Server Connector Port should be set to the specified port");

        Map<String, ConnectionFactory> factories = Whitebox.getInternalState(serverConnector, "_factories");

        assertTrue(
                factories.containsKey("ssl") && factories.get("ssl") != null, "Should return true because factory for SSL should have been set");

        SslConnectionFactory sslConnectionFactory = (SslConnectionFactory) factories.get("ssl");
        SslContextFactory sslContextFactory = sslConnectionFactory.getSslContextFactory();

        assertEquals(keystoreFile,
                sslContextFactory.getKeyStoreResource().getFile().getName(), "Should return the Keystore file specified");

        assertEquals(truststoreFile,
                sslContextFactory.getTrustStoreResource().getFile().getName(), "Should return the Truststore file specified");

    }

}
