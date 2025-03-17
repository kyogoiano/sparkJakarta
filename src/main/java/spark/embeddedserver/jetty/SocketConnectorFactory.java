/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.embeddedserver.jetty;

import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import org.jetbrains.annotations.NotNull;
import spark.ssl.SslStores;
import spark.utils.Assert;

/**
 * Creates socket connectors.
 */
public class SocketConnectorFactory {

    /**
     * Creates a ssl jetty socket jetty. Keystore required, truststore
     * optional. If truststore not specified keystore will be reused.
     *
     * @param server    Jetty server
     * @param sslStores the security sslStores.
     * @param host      host
     * @param port      port
     * @return a ssl socket jetty
     */
    public static ServerConnector createSecureSocketConnector(Server server,
                                                              String host,
                                                              int port,
                                                              SslStores sslStores,
                                                              boolean trustForwardHeaders) {
        Assert.notNull(server, "'server' must not be null");
        Assert.notNull(host, "'host' must not be null");

        if(sslStores == null){
            final HttpConnectionFactory httpConnectionFactory = createHttpConnectionFactory(trustForwardHeaders);
            final ServerConnector connector = new ServerConnector(server, httpConnectionFactory);
            initializeConnector(connector, host, port);
            return connector;
        }

        Assert.notNull(sslStores, "'sslStores' must not be null");

        SslContextFactory.Server sslContextFactory = getSslContextFactory(sslStores);

        HttpConnectionFactory httpConnectionFactory = createHttpConnectionFactory(trustForwardHeaders);

        ServerConnector connector = new ServerConnector(server, sslContextFactory, httpConnectionFactory);
        initializeConnector(connector, host, port);
        return connector;
    }

    private static SslContextFactory.@NotNull Server getSslContextFactory(final SslStores sslStores) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        if(sslStores.keyStoreType() != null) {
            sslContextFactory.setKeyStoreType(sslStores.keyStoreType());
        }

        sslContextFactory.setKeyStorePath(sslStores.keyStoreFile().getPath());

        if (sslStores.keyStorePassword() != null) {
            sslContextFactory.setKeyStorePassword(sslStores.keyStorePassword());
        }

        if (sslStores.certAlias() != null) {
            sslContextFactory.setCertAlias(sslStores.certAlias());
        }

        if (sslStores.trustStoreFile() != null) {
            sslContextFactory.setTrustStorePath(sslStores.trustStoreFile().getPath());
        }

        if (sslStores.trustStorePassword() != null) {
            sslContextFactory.setTrustStorePassword(sslStores.trustStorePassword());
        }

        if(sslStores.trustStoreType() != null) {
            sslContextFactory.setTrustStoreType(sslStores.trustStoreType());
        }

        if (sslStores.needsClientCert()) {
            sslContextFactory.setNeedClientAuth(true);
            sslContextFactory.setWantClientAuth(true);
        }
        return sslContextFactory;
    }

    private static void initializeConnector(ServerConnector connector, String host, int port) {
        // Set some timeout options to make debugging easier.
        connector.setIdleTimeout(TimeUnit.HOURS.toMillis(1));
        connector.setHost(host);
        connector.setPort(port);
    }

    private static HttpConnectionFactory createHttpConnectionFactory(boolean trustForwardHeaders) {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        if(trustForwardHeaders)
            httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        return new HttpConnectionFactory(httpConfig);
    }

}
