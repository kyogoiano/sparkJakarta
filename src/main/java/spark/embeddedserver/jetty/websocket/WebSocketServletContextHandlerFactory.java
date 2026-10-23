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

import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.eclipse.jetty.ee11.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.ee11.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee11.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
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
        return create(webSocketHandlers, null, null, webSocketIdleTimeoutMillis);
    }

    /**
     * Creates the context handler for Jetty-native and Jakarta WebSocket endpoints.
     */
    public static ServletContextHandler create(
            final Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers,
            final Map<String, Class<?>> jakartaAnnotatedEndpoints,
            final Map<String, ServerEndpointConfig> jakartaConfiguredEndpoints,
            final Long webSocketIdleTimeoutMillis) {
        boolean hasJettyEndpoints = webSocketHandlers != null && !webSocketHandlers.isEmpty();
        boolean hasJakartaEndpoints = (jakartaAnnotatedEndpoints != null && !jakartaAnnotatedEndpoints.isEmpty())
                || (jakartaConfiguredEndpoints != null && !jakartaConfiguredEndpoints.isEmpty());
        if (!hasJettyEndpoints && !hasJakartaEndpoints) {
            return null;
        }

        ServletContextHandler context = new ServletContextHandler("/", true, false);
        configure(context, webSocketHandlers, jakartaAnnotatedEndpoints, jakartaConfiguredEndpoints,
                webSocketIdleTimeoutMillis);
        return context;
    }

    /**
     * Adds WebSocket endpoint registration to an existing servlet context.
     */
    public static void configure(
            ServletContextHandler context,
            final Map<String, WebSocketHandlerWrapper<?>> webSocketHandlers,
            final Map<String, Class<?>> jakartaAnnotatedEndpoints,
            final Map<String, ServerEndpointConfig> jakartaConfiguredEndpoints,
            final Long webSocketIdleTimeoutMillis) {
        if (!context.isStopped()) {
            throw new IllegalStateException("WebSocket configuration must be added before the context starts");
        }

        boolean hasJettyEndpoints = webSocketHandlers != null && !webSocketHandlers.isEmpty();
        boolean hasJakartaEndpoints = (jakartaAnnotatedEndpoints != null && !jakartaAnnotatedEndpoints.isEmpty())
                || (jakartaConfiguredEndpoints != null && !jakartaConfiguredEndpoints.isEmpty());
        if (hasJettyEndpoints) {
            JettyWebSocketServletContainerInitializer.configure(context, (servletContext, serverContainer) -> {
                if (webSocketIdleTimeoutMillis != null) {
                    serverContainer.setIdleTimeout(Duration.ofMillis(webSocketIdleTimeoutMillis));
                }

                for (Map.Entry<String, WebSocketHandlerWrapper<?>> entry : webSocketHandlers.entrySet()) {
                    JettyWebSocketCreator creator = WebSocketCreatorFactory.create(entry.getValue());
                    serverContainer.addMapping(entry.getKey(), creator);
                    logger.info("Jetty WebSocket endpoint registered: {}", entry.getKey());
                }
            });
        }

        if (hasJakartaEndpoints) {
            JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, serverContainer) -> {
                if (webSocketIdleTimeoutMillis != null) {
                    serverContainer.setDefaultMaxSessionIdleTimeout(webSocketIdleTimeoutMillis);
                }

                registerJakartaEndpoints(serverContainer, jakartaAnnotatedEndpoints, jakartaConfiguredEndpoints);
            });
        }
    }

    private static void registerJakartaEndpoints(
            ServerContainer serverContainer,
            Map<String, Class<?>> annotatedEndpoints,
            Map<String, ServerEndpointConfig> configuredEndpoints) throws DeploymentException {
        for (Map.Entry<String, Class<?>> entry : nullSafeMap(annotatedEndpoints).entrySet()) {
            serverContainer.addEndpoint(entry.getValue());
            logger.info("Jakarta WebSocket endpoint registered: {}", entry.getKey());
        }

        for (Map.Entry<String, ServerEndpointConfig> entry : nullSafeMap(configuredEndpoints).entrySet()) {
            serverContainer.addEndpoint(entry.getValue());
            logger.info("Jakarta WebSocket endpoint registered: {}", entry.getKey());
        }
    }

    private static <T> Map<String, T> nullSafeMap(Map<String, T> endpoints) {
        return endpoints == null ? Collections.emptyMap() : endpoints;
    }

}
