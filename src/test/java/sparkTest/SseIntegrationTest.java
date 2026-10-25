package sparkTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import spark.Service;
import spark.SseEmitter;
import spark.SseEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseIntegrationTest {

    private final AtomicReference<SseEmitter> emitterRef = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> overflowFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> oversizedFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> customEventLimitFailure = new AtomicReference<>();
    private final AtomicReference<IllegalStateException> customByteLimitFailure = new AtomicReference<>();
    private Service service;

    @BeforeEach
    void startService() {
        service = Service.ignite();
        service.ipAddress("127.0.0.1");
        service.port(0);
        service.sse("/events", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send(SseEvent.builder("first line\nsecond line")
                    .event("update")
                    .id("1")
                    .retry(Duration.ofSeconds(1))
                    .build());
            emitter.send("second");
            emitter.complete();
        });
        service.sse("/overflow", (request, response, emitter) -> {
            emitterRef.set(emitter);
            try {
                emitter.send("x".repeat(SseEmitter.MAX_BUFFERED_BYTES));
            } catch (IllegalStateException failure) {
                oversizedFailure.set(failure);
            }
            for (int i = 0; i < SseEmitter.DEFAULT_MAX_BUFFERED_EVENTS; i++) {
                emitter.send("queued");
            }
            try {
                emitter.send("rejected");
            } catch (IllegalStateException failure) {
                overflowFailure.set(failure);
            }
            emitter.complete();
        });
        service.sse("/live", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("initial");
        });
        service.sse("/large", (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("x".repeat(300_000));
            emitter.complete();
        });
        service.sse("/custom-events", 2, 1024, (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("first");
            emitter.send("second");
            try {
                emitter.send("third");
            } catch (IllegalStateException failure) {
                customEventLimitFailure.set(failure);
            }
            emitter.complete();
        });
        service.sse("/custom-bytes", 10, 16, (request, response, emitter) -> {
            emitterRef.set(emitter);
            emitter.send("first");
            try {
                emitter.send("second");
            } catch (IllegalStateException failure) {
                customByteLimitFailure.set(failure);
            }
            emitter.complete();
        });
        service.get("/normal", (request, response) -> "ordinary response");
        service.awaitInitialization();
    }

    @AfterEach
    void stopService() {
        service.stop();
        service.awaitStop();
    }

    @Test
    void streamsFormattedEventsAndCompletesTheAsyncResponse() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/events");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElseThrow());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals("event:update", reader.readLine());
            assertEquals("id:1", reader.readLine());
            assertEquals("retry:1000", reader.readLine());
            assertEquals("data:first line", reader.readLine());
            assertEquals("data:second line", reader.readLine());
            assertEquals("", reader.readLine());
            assertEquals("data:second", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertTrue(emitterRef.get().isClosed(), "Completed SSE responses should release the emitter");
    }

    @Test
    void rejectsEventsWhenTheBoundedBufferIsFull() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/overflow");
        assertEquals(200, response.statusCode());
        assertNotNull(overflowFailure.get(), "The producer should be notified when the event queue is full");
        assertNotNull(oversizedFailure.get(), "Oversized individual events should be rejected");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            long lines = reader.lines().count();
            assertEquals(SseEmitter.DEFAULT_MAX_BUFFERED_EVENTS * 2L, lines);
        }
        assertTrue(emitterRef.get().isClosed(), "The completed overflow-test stream should release its emitter");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void sendsEventsAfterTheInitialRouteHasReturned() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/live");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:initial", reader.readLine());
            assertEquals("", reader.readLine());

            emitterRef.get().send("after open");
            emitterRef.get().complete();

            assertEquals("data:after open", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertTrue(emitterRef.get().isClosed());
    }

    @Test
    void supportsLargeTextEventsAndOrdinaryHttpOnTheSameServer() throws Exception {
        HttpResponse<java.io.InputStream> response = request("/large");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertEquals(300_005, reader.readLine().length());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }

        HttpResponse<String> ordinaryResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + "/normal"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ordinaryResponse.statusCode());
        assertEquals("ordinary response", ordinaryResponse.body());
    }

    @Test
    void routeCanCustomizeEventAndByteBufferLimits() throws Exception {
        HttpResponse<java.io.InputStream> eventLimitedResponse = request("/custom-events");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(eventLimitedResponse.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:first", reader.readLine());
            assertEquals("", reader.readLine());
            assertEquals("data:second", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertNotNull(customEventLimitFailure.get(), "The configured event-count limit should be enforced");

        HttpResponse<java.io.InputStream> byteLimitedResponse = request("/custom-bytes");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(byteLimitedResponse.body(), StandardCharsets.UTF_8))) {
            assertEquals("data:first", reader.readLine());
            assertEquals("", reader.readLine());
            assertNull(reader.readLine());
        }
        assertNotNull(customByteLimitFailure.get(), "The configured byte limit should be enforced");
    }

    @Test
    void rejectsInvalidCustomBufferLimitsAtRegistration() {
        Service uninitializedService = Service.ignite();
        assertThrows(IllegalArgumentException.class,
                () -> uninitializedService.sse("/invalid-events", 0, 1, (request, response, emitter) -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> uninitializedService.sse("/invalid-bytes", 1, 0, (request, response, emitter) -> { }));
    }

    private HttpResponse<java.io.InputStream> request(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + path))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }
}
