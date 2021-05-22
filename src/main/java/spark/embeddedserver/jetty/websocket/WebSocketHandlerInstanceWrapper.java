package spark.embeddedserver.jetty.websocket;

import static java.util.Objects.requireNonNull;

public record WebSocketHandlerInstanceWrapper(Object handler) implements WebSocketHandlerWrapper {

    public WebSocketHandlerInstanceWrapper {
        requireNonNull(handler, "WebSocket handler cannot be null");
        WebSocketHandlerWrapper.validateHandlerClass(handler.getClass());
    }

    @Override
    public Object getHandler() {
        return handler;
    }

}
