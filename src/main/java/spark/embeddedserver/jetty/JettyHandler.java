/*
 * Copyright 2011- Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.embeddedserver.jetty;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Simple Jetty Handler
 *
 * @author Per Wendel
 */
public class JettyHandler extends Handler.Abstract  {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JettyHandler.class);
    private final Filter filter;
    private SessionHandler sessionHandler;

    public JettyHandler(Filter filter) {
        this.filter = filter;
    }


    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        LOG.info("Handling request: {}", request);

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        HttpRequestWrapper wrapper = new HttpRequestWrapper(httpRequest);

        filter.doFilter(wrapper, httpResponse, null);

        boolean handled = response.isCommitted() || !wrapper.notConsumed();
        LOG.info("Response committed: {}, handled: {}", response.isCommitted(), handled);

        callback.succeeded(); // Signal that request processing is complete
        return handled;
    }

    public SessionHandler getSessionHandler() {
        return sessionHandler;
    }
}
