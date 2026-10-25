package spark;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseEventTest {

    @Test
    void encodesEventFieldsAndMultilineText() {
        SseEvent event = SseEvent.builder("first line\r\nsecond line")
                .event("update")
                .id("42")
                .retry(Duration.ofSeconds(3))
                .build();

        assertEquals("event:update\nid:42\nretry:3000\ndata:first line\ndata:second line\n\n", event.encode());
    }

    @Test
    void rejectsLineBreaksInEventMetadata() {
        assertThrows(IllegalArgumentException.class, () -> SseEvent.builder("data").event("update\nid:forged"));
        assertThrows(IllegalArgumentException.class, () -> SseEvent.builder("data").id("42\r\n"));
        assertThrows(IllegalArgumentException.class, () -> SseEvent.builder("data").id("nul\0id"));
        assertThrows(IllegalArgumentException.class,
                () -> SseEvent.builder("data").retry(Duration.ofMillis(-1)));
    }
}
