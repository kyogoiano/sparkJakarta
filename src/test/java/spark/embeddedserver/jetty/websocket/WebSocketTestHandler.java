package spark.embeddedserver.jetty.websocket;

import static java.util.Collections.synchronizedList;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

@WebSocket
public class WebSocketTestHandler {
    public static final List<String> events = synchronizedList(new ArrayList<>());

    @OnWebSocketConnect
    public void connected(Session session) {
	events.add("onConnect");
    }

    @OnWebSocketClose
    public void closed(int statusCode, String reason) {
	events.add(String.format("onClose: %s %s", statusCode, reason));
    }

    @OnWebSocketMessage
    public void message(String message) {
	events.add("onMessage: " + message);
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        System.out.println("Error: " + t.getMessage());
    }


}
