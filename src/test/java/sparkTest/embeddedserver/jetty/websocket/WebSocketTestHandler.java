package sparkTest.embeddedserver.jetty.websocket;

import static java.util.Collections.synchronizedList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.ee9.websocket.api.Frame;
import org.eclipse.jetty.ee9.websocket.api.Session;
import org.eclipse.jetty.ee9.websocket.api.annotations.*;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.core.OpCode;

@WebSocket
public class WebSocketTestHandler {
    public static final List<String> events = synchronizedList(new ArrayList<>());
    private static final ByteBuffer EMPTY_PAYLOAD = BufferUtil.toBuffer("");


    @OnWebSocketConnect
    public void connected(Session session) {
	    events.add("onConnect");
    }

    @OnWebSocketOpen
    public void onWebSocketOpen(Session session)
    {
        events.add("onWebSocketOpen");
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


    @OnWebSocketFrame
    public void onFrame(Frame frame) {
        if (OpCode.PONG == frame.getOpCode()) {
            ByteBuffer payload = frame.getPayload() != null ? frame.getPayload() : EMPTY_PAYLOAD;
            events.add("onFrame" + frame);
        }
    }

}
