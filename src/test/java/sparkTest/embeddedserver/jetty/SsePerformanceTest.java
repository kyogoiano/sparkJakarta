package sparkTest.embeddedserver.jetty;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;
import spark.SseEvent;
import sparkTest.util.SparkTestUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures SSE connection latency, throughput, response volume, and sampled heap growth.
 */
class SsePerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(SsePerformanceTest.class);
    private static final int WARMUP_CONNECTIONS = 5;
    private static final int BENCHMARK_CONNECTIONS = 80;
    private static final int CONCURRENCY = 4;
    private static final int EVENTS_PER_CONNECTION = 32;

    private record Result(
            String scenario,
            String protocol,
            int totalConnections,
            double totalTimeMs,
            double connectionsPerSecond,
            double avgLatencyMs,
            double p95LatencyMs,
            long responseBytes,
            long peakHeapIncreaseBytes,
            long peakNativeCommittedIncreaseBytes,
            long peakResidentSetIncreaseBytes) {
    }

    @Test
    void comparesHttpsAndHttp2SsePerformance() throws Exception {
        List<Result> results = new ArrayList<>();
        results.add(runBenchmark("HTTPS (Platform Threads)", false, false));
        results.add(runBenchmark("HTTPS (Virtual Threads)", false, true));
        results.add(runBenchmark("HTTP/2 (Platform Threads)", true, false));
        results.add(runBenchmark("HTTP/2 (Virtual Threads)", true, true));

        StringBuilder report = new StringBuilder("\n");
        report.append("============================================================================================================\n");
        report.append(String.format("%-32s | %-8s | %-7s | %-10s | %-14s | %-10s | %-10s | %-12s | %-12s | %-15s | %-12s%n",
                "Scenario", "Protocol", "Conns", "Total (ms)", "Connections/s", "Avg (ms)", "p95 (ms)",
                "Response MiB", "Heap +MiB", "NMT native +MiB", "RSS +MiB"));
        report.append("------------------------------------------------------------------------------------------------------------\n");
        for (Result result : results) {
            report.append(String.format("%-32s | %-8s | %7d | %10.2f | %14.2f | %10.2f | %10.2f | %12.2f | %10.2f | %15.2f | %12s%n",
                    result.scenario(),
                    result.protocol(),
                    result.totalConnections(),
                    result.totalTimeMs(),
                    result.connectionsPerSecond(),
                    result.avgLatencyMs(),
                    result.p95LatencyMs(),
                    result.responseBytes() / (1024.0 * 1024.0),
                    result.peakHeapIncreaseBytes() / (1024.0 * 1024.0),
                    result.peakNativeCommittedIncreaseBytes() / (1024.0 * 1024.0),
                    result.peakResidentSetIncreaseBytes() < 0
                            ? "N/A"
                            : String.format("%.2f",
                                    result.peakResidentSetIncreaseBytes() / (1024.0 * 1024.0))));
            assertEquals(BENCHMARK_CONNECTIONS, result.totalConnections());
            assertTrue(result.connectionsPerSecond() > 0,
                    "Connection throughput must be positive for " + result.scenario());
            assertTrue(result.responseBytes() > 0, "SSE response bytes must be recorded for " + result.scenario());
            assertTrue(result.peakHeapIncreaseBytes() >= 0,
                    "Heap increase must not be negative for " + result.scenario());
            assertTrue(result.peakNativeCommittedIncreaseBytes() >= 0,
                    "Native memory increase must not be negative for " + result.scenario());
            assertTrue(result.peakResidentSetIncreaseBytes() >= -1,
                    "RSS increase must be available or marked unsupported for " + result.scenario());
        }
        report.append("============================================================================================================\n");
        LOG.info(report.toString());
        System.out.println(report);
    }

    private Result runBenchmark(String scenario, boolean useHttp2, boolean useVirtualThreads) throws Exception {
        Service service = Service.ignite();
        service.ipAddress("localhost");
        service.port(0);
        service.secure(
                SparkTestUtil.getKeyStoreLocation(),
                SparkTestUtil.getKeystorePassword(),
                null,
                null);
        service.useVirtualThreads(useVirtualThreads);
        if (useHttp2) {
            service.useHttp2();
        }

        String eventPayload = "spark-sse-performance-event-".repeat(4);
        service.sse("/events", (request, response, emitter) -> {
            for (int i = 0; i < EVENTS_PER_CONNECTION; i++) {
                emitter.send(SseEvent.builder(eventPayload + i).event("benchmark").id(Integer.toString(i)).build());
            }
            emitter.complete();
        });

        try {
            service.awaitInitialization();
            String url = "https://localhost:" + service.port() + "/events";
            try (HttpClient client = Http2PerformanceTest.createJettyClient(useHttp2)) {
                client.start();
                for (int i = 0; i < WARMUP_CONNECTIONS; i++) {
                    verifyResponse(client, url);
                }

                long[] latenciesNanos = new long[BENCHMARK_CONNECTIONS];
                AtomicInteger nextConnection = new AtomicInteger();
                AtomicLong responseBytes = new AtomicLong();
                String protocol = useHttp2 ? "HTTP/2.0" : "HTTP/1.1";

                try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
                     PerformanceMemorySampler memorySampler = new PerformanceMemorySampler()) {
                    long startNanos = System.nanoTime();
                    CompletableFuture.allOf(IntStream.range(0, CONCURRENCY)
                            .mapToObj(worker -> CompletableFuture.runAsync(() -> {
                                int index;
                                while ((index = nextConnection.getAndIncrement()) < BENCHMARK_CONNECTIONS) {
                                    long requestStart = System.nanoTime();
                                    try {
                                        ContentResponse response = verifyResponse(client, url);
                                        latenciesNanos[index] = System.nanoTime() - requestStart;
                                        responseBytes.addAndGet(response.getContent().length);
                                    } catch (Exception failure) {
                                        throw new RuntimeException(
                                                "SSE benchmark connection failed in " + scenario
                                                        + ", connection index: " + index,
                                                failure);
                                    }
                                }
                            }, executor))
                            .toArray(CompletableFuture[]::new)).join();

                    double totalTimeMs = (System.nanoTime() - startNanos) / 1_000_000.0;
                    Arrays.sort(latenciesNanos);
                    double avgLatencyMs = Arrays.stream(latenciesNanos).average().orElse(0) / 1_000_000.0;
                    double p95LatencyMs = percentileMs(latenciesNanos, 0.95);

                    return new Result(
                            scenario,
                            protocol,
                            BENCHMARK_CONNECTIONS,
                            totalTimeMs,
                            BENCHMARK_CONNECTIONS * 1000.0 / totalTimeMs,
                            avgLatencyMs,
                            p95LatencyMs,
                            responseBytes.get(),
                            memorySampler.peakHeapIncreaseBytes(),
                            memorySampler.peakNativeCommittedIncreaseBytes(),
                            memorySampler.peakResidentSetIncreaseBytes());
                }
            }
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    private ContentResponse verifyResponse(HttpClient client, String url) throws Exception {
        ContentResponse response = client.newRequest(url)
                .timeout(15, TimeUnit.SECONDS)
                .send();
        assertEquals(200, response.getStatus());
        assertTrue(response.getMediaType().startsWith("text/event-stream"));
        long dataEvents = Arrays.stream(response.getContentAsString().split("\n"))
                .filter(line -> line.startsWith("data:"))
                .count();
        assertEquals(EVENTS_PER_CONNECTION, dataEvents);
        return response;
    }

    private double percentileMs(long[] sortedLatencies, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sortedLatencies.length) - 1);
        return sortedLatencies[index] / 1_000_000.0;
    }
}
