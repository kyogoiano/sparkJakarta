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
package spark.embeddedserver.jetty.websocket;

import jakarta.servlet.Servlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;

import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Creates websocket servlet context handlers.
 */
public class WebSocketServletContextHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServletContextHandlerFactory.class);

    /**
     * Creates a new WebSocket servlet context handler.
     *
     * @param webSocketHandlers          Map of WebSocket handlers
     * @param webSocketIdleTimeoutMillis WebSocket idle timeout in milliseconds
     * @param server                     Jetty server instance
     * @return a configured ServletContextHandler
     */
    public static ServletContextHandler create(final Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers,
                                               final Long webSocketIdleTimeoutMillis, final Server server) {
        ServletContextHandler webSocketServletContextHandler = null;

        if (webSocketHandlers != null) {
            try {
                webSocketServletContextHandler = new ServletContextHandler("/", true, false);

                // Attach the context handler to the server.
                server.setHandler(webSocketServletContextHandler);

                // Initialize Jetty WebSocket support
                ServletContextHandler finalWebSocketServletContextHandler = webSocketServletContextHandler;
                JettyWebSocketServletContainerInitializer.configure(webSocketServletContextHandler, (servletContext, serverContainer) -> {
                    if (webSocketIdleTimeoutMillis != null) {
                        serverContainer.setIdleTimeout(Duration.ofMillis(webSocketIdleTimeoutMillis));
                    }

                    // Ensure the WebSocket container is properly set up
                    JettyWebSocketServerContainer.ensureContainer(servletContext);

                    for (final Map.Entry<String, WebSocketHandlerWrapper<?>> entry : webSocketHandlers.entrySet()) {
                        final JettyWebSocketCreator webSocketCreator = WebSocketCreatorFactory.create(entry.getValue());

                        // Register WebSocket endpoint
                        serverContainer.addMapping(entry.getKey(), webSocketCreator);

                        logger.info("WebSocket endpoint registered: {}", entry.getKey());

                        // Register servlet to handle WebSocket upgrade
                        final Servlet webSocketServlet = new WebSocketServlet(entry.getKey(), webSocketCreator);
                        finalWebSocketServletContextHandler.addServlet(new ServletHolder(entry.getKey(), webSocketServlet), entry.getKey());
                    }

                    try {
                        serverContainer.start();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to start WebSocket container", e);
                    }
                });

            } catch (Exception ex) {
                logger.error("Creation of WebSocket context handler failed.", ex);
                webSocketServletContextHandler = null;
            }
        }
        return webSocketServletContextHandler;
    }

    /**
     * Inner class for WebSocket Servlet handling upgrades.
     */
    private static class WebSocketServlet extends jakarta.servlet.http.HttpServlet {
        private final String path;
        private final JettyWebSocketCreator creator;

        public WebSocketServlet(String path, JettyWebSocketCreator creator) {
            this.path = path;
            this.creator = creator;
        }

        @Override
        protected void doGet(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse resp) {
            // This method is required for servlet registration, but WebSocket upgrade is handled by Jetty
            logger.info("WebSocket servlet handling GET request at: {}", path);
        }
    }

}
