package sparkTest.embeddedserver.jetty;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures WebSocket connection and echo-message performance across Jetty thread models.
 */
class WebSocketPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPerformanceTest.class);
    private static final int WARMUP_CONNECTIONS = 5;
    private static final int BENCHMARK_CONNECTIONS = 80;
    private static final int CONCURRENCY = 4;
    private static final int MESSAGES_PER_CONNECTION = 32;
    private static final int MESSAGE_COUNT = BENCHMARK_CONNECTIONS * MESSAGES_PER_CONNECTION;

    private record Result(
            String scenario,
            int totalConnections,
            int totalMessages,
            double totalTimeMs,
            double connectionsPerSecond,
            double messagesPerSecond,
            double avgConnectionLatencyMs,
            double p95ConnectionLatencyMs,
            double avgMessageLatencyMs,
            double p95MessageLatencyMs,
            long responseBytes,
            long peakHeapIncreaseBytes,
            long peakNativeCommittedIncreaseBytes,
            long peakResidentSetIncreaseBytes) {
    }

    @Test
    void comparesPlatformAndVirtualThreadWebSocketPerformance() throws Exception {
        List<Result> results = new ArrayList<>();
        results.add(runBenchmark("Platform Threads", false));
        results.add(runBenchmark("Virtual Threads", true));

        StringBuilder report = new StringBuilder("\n");
        report.append("============================================================================================================================\n");
        report.append(String.format(
                "%-20s | %-7s | %-10s | %-14s | %-14s | %-11s | %-11s | %-11s | %-11s | %-12s | %-12s | %-15s | %-12s%n",
                "Scenario", "Conns", "Total (ms)", "Connections/s", "Messages/s", "Conn avg (ms)",
                "Conn p95 (ms)", "Msg avg (ms)", "Msg p95 (ms)", "Response MiB", "Heap +MiB",
                "NMT native +MiB", "RSS +MiB"));
        report.append("----------------------------------------------------------------------------------------------------------------------------\n");
        for (Result result : results) {
            report.append(String.format(
                    "%-20s | %7d | %10.2f | %14.2f | %14.2f | %11.2f | %11.2f | %11.2f | %11.2f | %12.2f | %12.2f | %15.2f | %12s%n",
                    result.scenario(),
                    result.totalConnections(),
                    result.totalTimeMs(),
                    result.connectionsPerSecond(),
                    result.messagesPerSecond(),
                    result.avgConnectionLatencyMs(),
                    result.p95ConnectionLatencyMs(),
                    result.avgMessageLatencyMs(),
                    result.p95MessageLatencyMs(),
                    result.responseBytes() / (1024.0 * 1024.0),
                    result.peakHeapIncreaseBytes() / (1024.0 * 1024.0),
                    result.peakNativeCommittedIncreaseBytes() / (1024.0 * 1024.0),
                    result.peakResidentSetIncreaseBytes() < 0
                            ? "N/A"
                            : String.format("%.2f", result.peakResidentSetIncreaseBytes() / (1024.0 * 1024.0))));
            assertEquals(BENCHMARK_CONNECTIONS, result.totalConnections());
            assertEquals(MESSAGE_COUNT, result.totalMessages());
            assertTrue(result.connectionsPerSecond() > 0,
                    "Connection throughput must be positive for " + result.scenario());
            assertTrue(result.messagesPerSecond() > 0,
                    "Message throughput must be positive for " + result.scenario());
            assertTrue(result.responseBytes() > 0,
                    "WebSocket response bytes must be recorded for " + result.scenario());
            assertTrue(result.peakHeapIncreaseBytes() >= 0,
                    "Heap increase must not be negative for " + result.scenario());
            assertTrue(result.peakNativeCommittedIncreaseBytes() >= 0,
                    "Native memory increase must not be negative for " + result.scenario());
            assertTrue(result.peakResidentSetIncreaseBytes() >= -1,
                    "RSS increase must be available or marked unsupported for " + result.scenario());
        }
        report.append("============================================================================================================================\n");
        LOG.info(report.toString());
        System.out.println(report);
    }

    private Result runBenchmark(String scenario, boolean useVirtualThreads) throws Exception {
        Service service = Service.ignite();
        service.ipAddress("127.0.0.1");
        service.port(0);
        service.useVirtualThreads(useVirtualThreads);
        service.webSocket("/echo", WebSocketEchoEndpoint.class);

        try {
            service.init();
            service.awaitInitialization();
            URI endpointUri = URI.create("ws://127.0.0.1:" + service.port() + "/echo");
            var container = ContainerProvider.getWebSocketContainer();
            for (int i = 0; i < WARMUP_CONNECTIONS; i++) {
                runConnection(container, endpointUri, i, false, null, null, null);
            }

            long[] connectionLatenciesNanos = new long[BENCHMARK_CONNECTIONS];
            long[] messageLatenciesNanos = new long[MESSAGE_COUNT];
            AtomicInteger nextConnection = new AtomicInteger();
            AtomicLong responseBytes = new AtomicLong();

            try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
                 PerformanceMemorySampler memorySampler = new PerformanceMemorySampler()) {
                long startNanos = System.nanoTime();
                CompletableFuture.allOf(IntStream.range(0, CONCURRENCY)
                        .mapToObj(worker -> CompletableFuture.runAsync(() -> {
                            int connectionIndex;
                            while ((connectionIndex = nextConnection.getAndIncrement()) < BENCHMARK_CONNECTIONS) {
                                try {
                                    runConnection(
                                            container,
                                            endpointUri,
                                            connectionIndex,
                                            true,
                                            connectionLatenciesNanos,
                                            messageLatenciesNanos,
                                            responseBytes);
                                } catch (Exception failure) {
                                    throw new RuntimeException(
                                            "WebSocket benchmark failed in " + scenario
                                                    + ", connection index: " + connectionIndex,
                                            failure);
                                }
                            }
                        }, executor))
                        .toArray(CompletableFuture[]::new)).join();

                double totalTimeMs = (System.nanoTime() - startNanos) / 1_000_000.0;
                Arrays.sort(connectionLatenciesNanos);
                Arrays.sort(messageLatenciesNanos);
                return new Result(
                        scenario,
                        BENCHMARK_CONNECTIONS,
                        MESSAGE_COUNT,
                        totalTimeMs,
                        BENCHMARK_CONNECTIONS * 1000.0 / totalTimeMs,
                        MESSAGE_COUNT * 1000.0 / totalTimeMs,
                        averageMs(connectionLatenciesNanos),
                        percentileMs(connectionLatenciesNanos, 0.95),
                        averageMs(messageLatenciesNanos),
                        percentileMs(messageLatenciesNanos, 0.95),
                        responseBytes.get(),
                        memorySampler.peakHeapIncreaseBytes(),
                        memorySampler.peakNativeCommittedIncreaseBytes(),
                        memorySampler.peakResidentSetIncreaseBytes());
            }
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    private void runConnection(
            jakarta.websocket.WebSocketContainer container,
            URI endpointUri,
            int connectionIndex,
            boolean measure,
            long[] connectionLatenciesNanos,
            long[] messageLatenciesNanos,
            AtomicLong responseBytes) throws Exception {
        WebSocketEchoClient client = new WebSocketEchoClient();
        long connectStart = System.nanoTime();
        Session session = container.connectToServer(client, endpointUri);
        if (measure) {
            connectionLatenciesNanos[connectionIndex] = System.nanoTime() - connectStart;
        }
        try {
            for (int messageIndex = 0; messageIndex < MESSAGES_PER_CONNECTION; messageIndex++) {
                String message = "spark-websocket-performance-" + connectionIndex + "-" + messageIndex;
                long messageStart = System.nanoTime();
                session.getBasicRemote().sendText(message);
                String response = client.awaitMessage();
                if (measure) {
                    messageLatenciesNanos[connectionIndex * MESSAGES_PER_CONNECTION + messageIndex] =
                            System.nanoTime() - messageStart;
                    responseBytes.addAndGet(response.getBytes(StandardCharsets.UTF_8).length);
                }
                assertEquals(message, response);
            }
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    private double averageMs(long[] sortedLatencies) {
        return Arrays.stream(sortedLatencies).average().orElse(0) / 1_000_000.0;
    }

    private double percentileMs(long[] sortedLatencies, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sortedLatencies.length) - 1);
        return sortedLatencies[index] / 1_000_000.0;
    }

    @ClientEndpoint
    public static class WebSocketEchoClient {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void onMessage(String message) {
            messages.add(message);
        }

        String awaitMessage() throws InterruptedException {
            String message = messages.poll(15, TimeUnit.SECONDS);
            assertNotNull(message, "Timed out waiting for a WebSocket echo message");
            return message;
        }
    }

    @WebSocket
    public static class WebSocketEchoEndpoint {
        @OnWebSocketMessage
        public void onMessage(org.eclipse.jetty.websocket.api.Session session, String message) {
            session.sendText(message, Callback.NOOP);
        }
    }
}
