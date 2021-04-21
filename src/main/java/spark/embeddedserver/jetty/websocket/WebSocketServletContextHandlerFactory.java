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

import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.core.Configuration;
import org.eclipse.jetty.websocket.core.server.WebSocketCreator;
import org.eclipse.jetty.websocket.core.server.WebSocketMappings;
import org.eclipse.jetty.websocket.server.internal.JettyServerFrameHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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
     * @return a new websocket servlet context handler or 'null' if creation failed.
     */
    public static ServletContextHandler create(Map<String, WebSocketHandlerWrapper> webSocketHandlers,
                                               Optional<Long> webSocketIdleTimeoutMillis) {
        ServletContextHandler webSocketServletContextHandler = null;
        if (webSocketHandlers != null) {
            try {
                webSocketServletContextHandler = new ServletContextHandler(null, "/", true, false);

                // Since we are configuring WebSockets before the ServletContextHandler and WebSocketUpgradeFilter is
                // even initialized / started, then we have to pre-populate the configuration that will eventually
                // be used by Jetty's WebSocketUpgradeFilter.
                WebSocketMappings webSocketMappings = (WebSocketMappings) webSocketServletContextHandler
                    .getServletContext().getAttribute(WebSocketMappings.class.getName());
                for (String path : webSocketHandlers.keySet()) {
                    WebSocketCreator webSocketCreator = WebSocketCreatorFactory.create(webSocketHandlers.get(path));
                    Configuration.ConfigurationCustomizer customizer = new Configuration.ConfigurationCustomizer();
                    if (webSocketIdleTimeoutMillis.isPresent()) {
                        customizer.setIdleTimeout(Duration.ofMillis(webSocketIdleTimeoutMillis.get()));
                    }
                    webSocketMappings.addMapping(new ServletPathSpec(path), webSocketCreator,
                        JettyServerFrameHandlerFactory.getFactory(webSocketServletContextHandler.getServletContext()), customizer);
                }
            } catch (Exception ex) {
                logger.error("creation of websocket context handler failed.", ex);
                webSocketServletContextHandler = null;
            }
        }
        return webSocketServletContextHandler;
    }

}
