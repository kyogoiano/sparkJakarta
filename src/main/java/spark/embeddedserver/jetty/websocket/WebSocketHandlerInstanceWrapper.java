package spark.embeddedserver.jetty.websocket;

import static java.util.Objects.requireNonNull;

public record WebSocketHandlerInstanceWrapper<T>(T handler) implements WebSocketHandlerWrapper<T> {

    public WebSocketHandlerInstanceWrapper {
        requireNonNull(handler, "WebSocket handler cannot be null");
        WebSocketHandlerWrapper.validateHandlerClass(handler.getClass());
    }

    @Override
    public T getHandler() {
        return handler;
    }

}
