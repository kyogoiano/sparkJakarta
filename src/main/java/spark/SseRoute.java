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

/**
 * Handles a Server-Sent Events connection.
 */
@FunctionalInterface
public interface SseRoute {

    /**
     * Invoked for each request to the SSE endpoint.
     *
     * @param request the request
     * @param response the response
     * @param emitter the event emitter
     * @throws Exception if the connection cannot be initialized
     */
    void handle(Request request, Response response, SseEmitter emitter) throws Exception;
}
