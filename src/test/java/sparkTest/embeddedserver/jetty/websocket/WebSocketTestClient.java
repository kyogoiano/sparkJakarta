package sparkTest.embeddedserver.jetty.websocket;

import jakarta.websocket.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import static java.util.Collections.synchronizedList;


@ClientEndpoint
public class WebSocketTestClient {
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    public static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    public static final List<String> events = synchronizedList(new ArrayList<>());

    public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(duration, unit);
    }

    @OnOpen
    public void onConnect(Session session) throws IOException {
        // Store session
        sessions.add(session);
        System.out.println("WebSocket Connected: " + session.getId());

        // Send message asynchronously
        session.getAsyncRemote().sendText("Hi Spark!");

        // Close session after sending
        session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Bye!"));

        // Log event
        events.add("onConnect");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received Message: " + message);
        events.add("onMessage: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        closeLatch.countDown();
        sessions.remove(session);
        System.out.println("WebSocket Closed: " + closeReason);
        events.add("onClose: " + closeReason.getCloseCode() + " " + closeReason.getReasonPhrase());
    }

    @OnError
    public void onError(Session session, Throwable t) {
        System.err.println("WebSocket Error: " + t.getMessage());
        events.add("onError: " + t.getMessage());
    }
}
