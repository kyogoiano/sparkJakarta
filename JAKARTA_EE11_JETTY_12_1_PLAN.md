# Migration Plan: Jakarta EE 11 & Jetty 12.1 (JDK 21 Baseline)

This document outlines the step-by-step plan to migrate **sparkJakarta** from **Jakarta EE 10 / Jetty 12.0 (`ee10`)** to **Jakarta EE 11 / Jetty 12.1 (`ee11`)**, keeping **JDK 21** as the minimum required Java version, and introducing key Jakarta EE 11 / Jetty 12.1 / JDK 21 features.

---

## 1. Goals & Constraints

- **Target Platform**: Jakarta EE 11 (Jakarta Servlet 6.1, Jakarta WebSocket 2.2)
- **Target Embedded Server**: Eclipse Jetty `12.1.14` (`ee11` environment)
- **Minimum JDK**: JDK 21 (`sourceCompatibility = JavaVersion.VERSION_21`, `targetCompatibility = JavaVersion.VERSION_21`)
- **Build System**: Gradle

---

## 2. Dependency & Build Configuration (`build.gradle`)

Update [build.gradle](build.gradle):

1. **Jetty Version**:
   - Update `ext.jettyVersion` from `'12.0.40'` to `'12.1.14'`.
2. **Jakarta EE 11 / Jetty `ee11` Artifacts**:
   - Replace `org.eclipse.jetty.ee10:jetty-ee10-webapp:$jettyVersion` with `org.eclipse.jetty.ee11:jetty-ee11-webapp:$jettyVersion` (transitively provides `jakarta.servlet:jakarta.servlet-api:6.1.0`).
   - Replace `org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-jetty-server:$jettyVersion` with `org.eclipse.jetty.ee11.websocket:jetty-ee11-websocket-jetty-server:$jettyVersion`.
   - Replace `org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-jakarta-server:$jettyVersion` with `org.eclipse.jetty.ee11.websocket:jetty-ee11-websocket-jakarta-server:$jettyVersion`.
   - Replace `org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-jakarta-client:$jettyVersion` (`testImplementation`) with `org.eclipse.jetty.ee11.websocket:jetty-ee11-websocket-jakarta-client:$jettyVersion`.
3. **Jakarta WebSocket API**:
   - Upgrade `jakarta.websocket:jakarta.websocket-api` from `2.1.0` to `2.2.0` (Jakarta EE 11).

---

## 3. Java Module Descriptor (`src/main/java/module-info.java`)

Update [module-info.java](src/main/java/module-info.java) to require the `ee11` modules:

```java
module sparkJakarta {
    requires org.jetbrains.annotations;
    requires org.eclipse.jetty.ee11.servlet;
    requires org.eclipse.jetty.ee11.websocket.jetty.server;
    requires org.eclipse.jetty.ee11.websocket.jakarta.server;
    requires org.eclipse.jetty.websocket.server;
    // exports & opens ...
}
```

---

## 4. Main Source Code Migration (`org.eclipse.jetty.ee10.*` -> `org.eclipse.jetty.ee11.*`)

Replace `org.eclipse.jetty.ee10.*` packages with `org.eclipse.jetty.ee11.*` and adapt to Jetty 12.1 / Servlet 6.1 API changes:

1. **[EmbeddedJettyFactory.java](src/main/java/spark/embeddedserver/jetty/EmbeddedJettyFactory.java)**
   - `import org.eclipse.jetty.ee10.servlet.SessionHandler;` -> `import org.eclipse.jetty.ee11.servlet.SessionHandler;`
2. **[JettyHandler.java](src/main/java/spark/embeddedserver/jetty/JettyHandler.java)**
   - `import org.eclipse.jetty.ee10.servlet.SessionHandler;` -> `import org.eclipse.jetty.ee11.servlet.SessionHandler;`
3. **[EmbeddedJettyServer.java](src/main/java/spark/embeddedserver/jetty/EmbeddedJettyServer.java)**
   - `import org.eclipse.jetty.ee10.servlet.*;` -> `import org.eclipse.jetty.ee11.servlet.*;`
4. **[WebSocketCreatorFactory.java](src/main/java/spark/embeddedserver/jetty/websocket/WebSocketCreatorFactory.java)**
   - `org.eclipse.jetty.ee10.websocket.server.*` -> `org.eclipse.jetty.ee11.websocket.server.*`
5. **[WebSocketServletContextHandlerFactory.java](src/main/java/spark/embeddedserver/jetty/websocket/WebSocketServletContextHandlerFactory.java)**
   - `org.eclipse.jetty.ee10.servlet.*` -> `org.eclipse.jetty.ee11.servlet.*`
   - `org.eclipse.jetty.ee10.websocket.server.*` -> `org.eclipse.jetty.ee11.websocket.server.*`
6. **[SparkFilter.java](src/main/java/spark/servlet/SparkFilter.java)**
   - Verify `MimeTypes.UTF8` / `StandardCharsets.UTF_8.name()` compatibility with Jetty 12.1 `org.eclipse.jetty.http.MimeTypes`.

---

## 5. New Jakarta EE 11 / Jetty 12.1 / JDK 21 Features

### 5.1. Virtual Threads Support (JDK 21 + Jetty 12.1)
- **[Service.java](src/main/java/spark/Service.java)** & **[Spark.java](src/main/java/spark/Spark.java)**:
  - Add `useVirtualThreads(boolean useVirtualThreads)` (and `useVirtualThreads()` convenience method) on `Service` and static `Spark`.
- **[EmbeddedServer.java](src/main/java/spark/embeddedserver/EmbeddedServer.java)** & **[EmbeddedJettyServer.java](src/main/java/spark/embeddedserver/jetty/EmbeddedJettyServer.java)**:
  - Configure `QueuedThreadPool.setVirtualThreadsExecutor(Executors.newVirtualThreadPerTaskExecutor())` when virtual threads are enabled on the Jetty server's `QueuedThreadPool`.

### 5.2. Jakarta Cookie Attributes (`SameSite` & Custom Attributes)
- **[Response.java](src/main/java/spark/Response.java)** & **[ResponseWrapper.java](src/main/java/spark/http/matching/ResponseWrapper.java)**:
  - Leverage `jakarta.servlet.http.Cookie#setAttribute(String, String)` to add overloads:
    - `cookie(String domain, String path, String name, String value, int maxAge, boolean secured, boolean httpOnly, String sameSite)`
    - `cookie(String name, String value, int maxAge, boolean secured, boolean httpOnly, String sameSite)`
    - `cookie(String domain, String path, String name, String value, int maxAge, boolean secured, boolean httpOnly, Map<String, String> attributes)`

### 5.3. Jakarta Servlet 6.1 Native Status-Code Redirects & `103 Early Hints`
- **[Response.java](src/main/java/spark/Response.java)** & **[ResponseWrapper.java](src/main/java/spark/http/matching/ResponseWrapper.java)**:
  - Update `Response.redirect(String location, int httpStatusCode)` to use Jakarta Servlet 6.1's native `HttpServletResponse.sendRedirect(location, httpStatusCode)`.
  - Add `Response.sendEarlyHints()` utilizing Servlet 6.1's `HttpServletResponse.SC_EARLY_HINTS` (`103`) / `response.sendError(103)` or informational response support.

### 5.4. Optional HTTP/2 Support (Jetty 12.1 + JDK 21 / JDK 25 FFM)
- **[Service.java](src/main/java/spark/Service.java)** & **[Spark.java](src/main/java/spark/Spark.java)**:
  - Add opt-in `useHttp2()`, `useHttp2(boolean)` methods.
- **[SocketConnectorFactory.java](src/main/java/spark/embeddedserver/jetty/SocketConnectorFactory.java)** & **[EmbeddedJettyServer.java](src/main/java/spark/embeddedserver/jetty/EmbeddedJettyServer.java)**:
  
---

## 6. Test Suite & WebApp Descriptor Migration (`src/test/`)

1. **[EmbeddedJettyFactoryTest.java](src/test/java/sparkTest/embeddedserver/jetty/EmbeddedJettyFactoryTest.java)**
   - `org.eclipse.jetty.ee10.servlet.ServletContextHandler` -> `org.eclipse.jetty.ee11.servlet.ServletContextHandler`
2. **[WebSocketCreatorFactoryTest.java](src/test/java/sparkTest/embeddedserver/jetty/websocket/WebSocketCreatorFactoryTest.java)**
   - `org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator` -> `org.eclipse.jetty.ee11.websocket.server.JettyWebSocketCreator`
3. **[WebSocketServletContextHandlerFactoryTest.java](src/test/java/sparkTest/embeddedserver/jetty/websocket/WebSocketServletContextHandlerFactoryTest.java)**
   - Update `org.eclipse.jetty.ee10.*` imports to `org.eclipse.jetty.ee11.*`.
   - Update `WebSocketUpgradeFilter` filter name assertions to `"org.eclipse.jetty.ee11.websocket.servlet.WebSocketUpgradeFilter"`.
4. **[ServletTest.java](src/test/java/sparkTest/servlet/ServletTest.java)** & **[web.xml](src/test/webapp/WEB-INF/web.xml)**
   - `org.eclipse.jetty.ee10.webapp.WebAppContext` -> `org.eclipse.jetty.ee11.webapp.WebAppContext`
   - Update `src/test/webapp/WEB-INF/web.xml` schema version from `5.0` to `6.0`/`6.1`.
5. **[JakartaSessionAdapter.java](src/test/java/sparkTest/embeddedserver/jetty/websocket/JakartaSessionAdapter.java)**
   - Adapt to any changes in `jakarta.websocket:jakarta.websocket-api:2.2.0` and Jetty 12.1 `CoreSession`.
6. **Feature & Performance Tests**:
   - Add tests for **Virtual Threads** (`Thread.currentThread().isVirtual()` inside route handlers when `useVirtualThreads(true)` is enabled).
   - Add tests for **Cookie `SameSite` and custom attributes** in `CookiesIntegrationTest` / `ResponseTest` / `ResponseWrapperDelegationTest`.
   - Update/add tests for **Servlet 6.1 `sendRedirect(location, statusCode)`** and **Early Hints (103)** in `ResponseTest` / `RedirectTest`.
   - Add unit & integration tests for **Optional HTTP/2 ** in `SocketConnectorFactoryTest` and `EmbeddedJettyServerTest`, plus a concurrent benchmark suite in **[Http2PerformanceTest.java](src/test/java/sparkTest/embeddedserver/jetty/Http2PerformanceTest.java)** comparing the current HTTPS (HTTP/1.1 over TLS) implementation against HTTP/2 across Platform Threads and Virtual Threads.

---

## 7. Documentation & Verification

1. Update [README.md](README.md) badges, feature list, and examples for Jakarta EE 11, Jetty `12.1.14` (`ee11`), Virtual Threads, HTTP/2, and `SameSite` cookie attributes.
2. Run full Gradle build and test suite (`gradle clean test --rerun-tasks`) on JDK 21+ / JDK 25 and verify 100% of active tests pass.
