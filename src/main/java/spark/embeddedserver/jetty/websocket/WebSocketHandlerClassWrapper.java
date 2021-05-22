package spark.embeddedserver.jetty.websocket;

import java.lang.reflect.InvocationTargetException;

import static java.util.Objects.requireNonNull;

public record WebSocketHandlerClassWrapper(Class<?> handlerClass) implements WebSocketHandlerWrapper {

    public WebSocketHandlerClassWrapper {
        requireNonNull(handlerClass, "WebSocket handler class cannot be null");
        WebSocketHandlerWrapper.validateHandlerClass(handlerClass);
    }

    @Override
    public Object getHandler() {
        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
            throw new RuntimeException("Could not instantiate websocket handler", ex);
        }
    }

}
