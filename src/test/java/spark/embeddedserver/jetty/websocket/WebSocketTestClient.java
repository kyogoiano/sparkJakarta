package spark.embeddedserver.jetty.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.*;

import static java.util.Collections.synchronizedList;

@WebSocket
public class WebSocketTestClient {
    private final CountDownLatch closeLatch;
    public static final List<String> events = synchronizedList(new ArrayList<>());


    public WebSocketTestClient() {
        closeLatch = new CountDownLatch(1);
    }

    public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(duration, unit);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        closeLatch.countDown();
        events.add(String.format("onClose: %s %s", statusCode, reason));
    }

    @OnWebSocketConnect
    public void onConnect(Session session) throws IOException{
	session.getRemote().sendString("Hi Spark!");
	session.close(StatusCode.NORMAL, "Bye!");
	events.add("onConnect");
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        System.out.println("Error: " + t.getMessage());
        events.add("onError");
    }

    @OnWebSocketMessage
    public void message(String message) {
        events.add("onMessage: " + message);
    }
}
