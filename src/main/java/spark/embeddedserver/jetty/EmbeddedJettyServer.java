/*
 * Copyright 2011- Per Wendel
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

import org.eclipse.jetty.ee10.servlet.*;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.core.server.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerWrapper;
import spark.embeddedserver.jetty.websocket.WebSocketServletContextHandlerFactory;
import spark.ssl.SslStores;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Spark server implementation
 *
 * @author Per Wendel
 */
public class EmbeddedJettyServer implements EmbeddedServer {

    private static final int SPARK_DEFAULT_PORT = 4567;
    private static final String NAME = "Spark";

    private final JettyServerFactory serverFactory;
    private final JettyHandler handler;
    private Server server;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers;
    private Long webSocketIdleTimeoutMillis;

    private ThreadPool threadPool = null;
    private boolean trustForwardHeaders = true; // true by default

    public EmbeddedJettyServer(JettyServerFactory serverFactory, JettyHandler handler) {
        this.serverFactory = serverFactory;
        this.handler = handler;
    }

    @Override
    public void configureWebSockets(Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers,
                                    Long webSocketIdleTimeoutMillis) {

        this.webSocketHandlers = webSocketHandlers;
        this.webSocketIdleTimeoutMillis = webSocketIdleTimeoutMillis;
    }

    @Override
    public void trustForwardHeaders(boolean trust) {
        this.trustForwardHeaders = trust;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int ignite(String host,
                      int port,
                      SslStores sslStores,
                      int maxThreads,
                      int minThreads,
                      int threadIdleTimeoutMillis) throws Exception {

        boolean hasCustomizedConnectors = false;

        if (port == 0) {
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            } catch (IOException e) {
                logger.error("Could not get first available port (port set to 0), using default: {}", SPARK_DEFAULT_PORT);
                port = SPARK_DEFAULT_PORT;
            }
        }

        // Create instance of jetty server with either default or supplied queued thread pool
        if(threadPool == null) {
            server = serverFactory.create(maxThreads, minThreads, threadIdleTimeoutMillis);
        } else {
            server = serverFactory.create(threadPool);
        }

        try (final ServerConnector connector =
                 SocketConnectorFactory.createSecureSocketConnector(server, host, port, sslStores, trustForwardHeaders)) {

            final Connector[] previousConnectors = server.getConnectors();

            // Jetty 12: Set server explicitly instead of relying on `connector.getServer()`
            server = connector.getServer();

            if (previousConnectors.length != 0) {
                server.setConnectors(previousConnectors);
                hasCustomizedConnectors = true;
            } else {
                server.setConnectors(new Connector[]{connector});
            }
        }

        final ServletContextHandler webSocketServletContextHandler =
            WebSocketServletContextHandlerFactory.create(webSocketHandlers, webSocketIdleTimeoutMillis, server);

        // Handle API routes
        ServletHandler servletHandler = new ServletHandler();

        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setServer(server);
        sessionHandler.setHandler(handler);

        ServletContextHandler servletContextHandler = new ServletContextHandler(sessionHandler, null, servletHandler, null);
        servletContextHandler.setContextPath("/*");
        servletContextHandler.setServletHandler(servletHandler);

        // Ensure handlers are properly set
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.setHandlers(servletContextHandler);
        server.setHandler(handlers);

        ContextHandlerCollection handlerCollection = new ContextHandlerCollection();
        handlerCollection.addHandler(servletContextHandler);


        ResourceFactory resourceFactory = ResourceFactory.of(servletContextHandler);
        //servletContextHandler.setBaseResource(resourceFactory.newResource(mainResourceBase));
        ServletHolder holderAlt = new ServletHolder("static-alt", DefaultServlet.class);
        holderAlt.setInitParameter("dirAllowed", "true");
        holderAlt.setInitParameter("acceptRanges", "true");
        servletContextHandler.addServlet(holderAlt, "*.js");
        ServletHolder holderstat = new ServletHolder("static", DefaultServlet.class);
        holderstat.setInitParameter("dirAllowed", "true");
        holderstat setInitParameter("acceptRanges", "true");
        holderstat.setInitParameter("cacheControl", "no-store");
        context.addServlet(holderstat , "/index.html");
        ServletHolder holderDef = new ServletHolder("default", DefaultServlet.class);
        holderDef.setInitParameter("dirAllowed", "true");
        context.addServlet(holderDef, "/");

        if (webSocketServletContextHandler != null) {

            // WebSocket upgrade handling (Jetty 12 uses WebSocketCore)
            WebSocketUpgradeHandler wsHandler = new WebSocketUpgradeHandler();
            webSocketServletContextHandler.setHandler(wsHandler);
            handlerCollection.addHandler(webSocketServletContextHandler);
        }

        //webSocketServletContextHandler.start();
        for (final Handler handler : handlerCollection.getHandlers()) {
            logger.info("handler inside collection: {}", handler.toString());
        }

        server.setHandler(handlerCollection);
        server.getScheduler().start();
        logger.info("== {} has ignited ...", NAME);
        if (hasCustomizedConnectors) {
            logger.info(">> Listening on Custom Server ports!");
        } else {
            logger.info(">> Listening on {}:{}", host, port);
        }

        server.start();

        for (Handler h : server.getHandlers()) {
            logger.info("Configured Handler: {}", h.getClass().getName());
        }
        return port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join() throws InterruptedException {
        server.join();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void extinguish() {
        logger.info(">>> {} shutting down ...", NAME);
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            logger.error("stop failed", e);
            System.exit(100); // NOSONAR
        }
        logger.info("done");
    }

    @Override
    public int activeThreadCount() {
        if (server == null) {
            return 0;
        }
        return server.getThreadPool().getThreads() - server.getThreadPool().getIdleThreads();
    }

    /**
     * Sets optional thread pool for jetty server.  This is useful for overriding the default thread pool
     * behaviour for example io.dropwizard.metrics.jetty9.InstrumentedQueuedThreadPool.
     * @param threadPool thread pool
     * @return Builder pattern - returns this instance
     */
    public EmbeddedJettyServer withThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
        return this;
    }
}
