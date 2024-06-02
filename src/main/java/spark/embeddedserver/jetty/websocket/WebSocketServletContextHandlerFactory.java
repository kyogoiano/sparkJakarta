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
import org.eclipse.jetty.ee9.websocket.jakarta.server.JakartaWebSocketServerContainer;
import org.eclipse.jetty.ee9.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee9.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee9.websocket.server.JettyWebSocketServletFactory;

import org.eclipse.jetty.ee9.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;

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
     * Creates a new websocket servlet context handler.
     *
     * @param webSocketHandlers          webSocketHandlers
     * @param webSocketIdleTimeoutMillis webSocketIdleTimeoutMillis
     * @param server jetty Server
     * @return a new websocket servlet context handler or 'null' if creation failed.
     */
    public static ServletContextHandler create(final Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers,
                                               final Long webSocketIdleTimeoutMillis, final Server server) {
        ServletContextHandler webSocketServletContextHandler = null;
        if (webSocketHandlers != null) {
            try {
                webSocketServletContextHandler = new ServletContextHandler(server, "/", true, false);

                for (final Map.Entry<String, WebSocketHandlerWrapper<?>> entry : webSocketHandlers.entrySet()) {

                    final JettyWebSocketCreator webSocketCreator = WebSocketCreatorFactory.create(webSocketHandlers.get(entry.getKey()));

                    final Servlet webSocketServlet = new JettyWebSocketServlet() {
                        @Override
                        protected void configure(final JettyWebSocketServletFactory factory) {
                            factory.addMapping(entry.getKey(), (req, res) -> webSocketCreator);
                            factory.register(entry.getValue().getHandler().getClass());
                        }
                    };

                    webSocketServletContextHandler.addServlet(new ServletHolder(entry.getValue().getHandler().getClass().getSimpleName(), webSocketServlet), entry.getKey());


//                    WebSocketUpgradeHandler webSocketUpgradeHandler = WebSocketUpgradeHandler.from(server, webSocketServletContextHandler.getCoreContextHandler(), container ->
//                        container.addMapping(entry.getKey(), (serverUpgradeRequest, serverUpgradeResponse, callback) -> {
//
//                            logger.debug("Upgrading WebSocket method: {}", serverUpgradeRequest.getMethod());
//
//                            return webSocketCreator.createWebSocket((JettyServerUpgradeRequest) serverUpgradeRequest, (JettyServerUpgradeResponse) serverUpgradeResponse);
//                        }));


                    JettyWebSocketServletContainerInitializer.configure(webSocketServletContextHandler, (servletContext, container) -> {
                        if(webSocketIdleTimeoutMillis != null) {
                            container.setIdleTimeout(Duration.ofMillis(webSocketIdleTimeoutMillis));
                        }

                        JakartaWebSocketServerContainer jakartaWebSocketServerContainer =
                            JakartaWebSocketServerContainer.ensureContainer(servletContext);
                        try {
                            jakartaWebSocketServerContainer.start();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });


//                    WebSocketComponents webSocketComponents =
//                        WebSocketServerComponents.ensureWebSocketComponents(server, webSocketServletContextHandler.getCoreContextHandler());
//
//                    WebSocketMappings mappings = WebSocketMappings.ensureMappings(webSocketServletContextHandler.getCoreContextHandler());
//
//                    webSocketComponents.start();
//
//                    logger.debug("WebSocketMappings: {}", mappings.toString());


                }



            } catch (Exception ex) {
                logger.error("creation of websocket context handler failed.", ex);
                webSocketServletContextHandler = null;
            }
        }
        return webSocketServletContextHandler;
    }

}
