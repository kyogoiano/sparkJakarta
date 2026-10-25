/*
 * Copyright 2026
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package spark;

import java.time.Duration;
import java.util.Objects;

/**
 * A text event sent over a Server-Sent Events connection.
 */
public final class SseEvent {

    private final String data;
    private final String event;
    private final String id;
    private final Long retryMillis;

    private SseEvent(Builder builder) {
        data = Objects.requireNonNull(builder.data, "SSE event data cannot be null");
        event = builder.event;
        id = builder.id;
        retryMillis = builder.retryMillis;
    }

    public static Builder builder(String data) {
        return new Builder(data);
    }

    String encode() {
        StringBuilder encoded = new StringBuilder();
        if (event != null) {
            encoded.append("event:").append(event).append('\n');
        }
        if (id != null) {
            encoded.append("id:").append(id).append('\n');
        }
        if (retryMillis != null) {
            encoded.append("retry:").append(retryMillis).append('\n');
        }
        for (String line : data.split("\\r\\n|\\r|\\n", -1)) {
            encoded.append("data:").append(line).append('\n');
        }
        return encoded.append('\n').toString();
    }

    long encodedCharacterLength() {
        long length = event == null ? 0 : 7L + event.length();
        length += id == null ? 0 : 4L + id.length();
        length += retryMillis == null ? 0 : 7L + retryMillis.toString().length();
        int lineLength = 0;
        int lineCount = 1;
        for (int i = 0; i < data.length(); i++) {
            char current = data.charAt(i);
            if (current == '\r') {
                if (i + 1 < data.length() && data.charAt(i + 1) == '\n') {
                    i++;
                }
                lineCount++;
            } else if (current == '\n') {
                lineCount++;
            } else {
                lineLength++;
            }
        }
        length += lineLength + 6L * lineCount + 1;
        return length;
    }

    public static final class Builder {
        private final String data;
        private String event;
        private String id;
        private Long retryMillis;

        private Builder(String data) {
            this.data = Objects.requireNonNull(data, "SSE event data cannot be null");
        }

        public Builder event(String event) {
            this.event = validateSingleLine(event, "event");
            return this;
        }

        public Builder id(String id) {
            this.id = validateSingleLine(id, "id");
            if (id != null && id.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("SSE event id cannot contain a null character");
            }
            return this;
        }

        public Builder retry(Duration retry) {
            Objects.requireNonNull(retry, "SSE retry duration cannot be null");
            if (retry.isNegative()) {
                throw new IllegalArgumentException("SSE retry duration cannot be negative");
            }
            try {
                retryMillis = retry.toMillis();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("SSE retry duration is too large", e);
            }
            return this;
        }

        public SseEvent build() {
            return new SseEvent(this);
        }

        private static String validateSingleLine(String value, String field) {
            if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
                throw new IllegalArgumentException("SSE " + field + " cannot contain a line break");
            }
            return value;
        }
    }
}
