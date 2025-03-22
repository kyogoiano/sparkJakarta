package sparkTest.embeddedserver.jetty.websocket;

// Update these imports to the new package names available in Jetty 12
import org.eclipse.jetty.util.Callback;
import jakarta.websocket.CloseReason;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.FrameHandler;

import java.io.IOException;

public class WebSocketTestClientFrameHandler implements FrameHandler {

    private final WebSocketTestClient delegate;
    private CoreSession coreSession; // Stored for later use

    public WebSocketTestClientFrameHandler(WebSocketTestClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onOpen(CoreSession session, Callback callback) {
        this.coreSession = session;
        try {
            // Convert the CoreSession to a Jakarta WebSocket Session.
            final var jakartaSession = new JakartaSessionAdapter(coreSession);
            delegate.onConnect(jakartaSession);
            callback.succeeded();
        } catch (IOException e) {
            callback.failed(e);
        }
    }

    @Override
    public void onFrame(Frame frame, Callback callback) {
        delegate.onMessage(frame.getPayloadAsUTF8());
        callback.succeeded();
    }


    @Override
    public void onError(Throwable throwable, Callback callback) {
        try {
            final var jakartaSession = new JakartaSessionAdapter(coreSession);
            delegate.onError(jakartaSession, throwable);
        } catch (Exception e) {
            // Optionally log the conversion error.
        }
        callback.succeeded();
    }

    @Override
    public void onClosed(CloseStatus closeStatus, Callback callback) {
        try {

            final var jakartaSession = new JakartaSessionAdapter(coreSession);
            // Convert Jetty's CloseStatus to a Jakarta CloseReason.
            CloseReason closeReason = new CloseReason(
                CloseReason.CloseCodes.getCloseCode(closeStatus.getCode()),
                closeStatus.getReason()
            );
            delegate.onClose(jakartaSession, closeReason);
            callback.succeeded();
        } catch (Exception e) {
            callback.failed(e);
        }
    }

}

