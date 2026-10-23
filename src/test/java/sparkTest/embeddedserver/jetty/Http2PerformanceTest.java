package sparkTest.embeddedserver.jetty;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;
import sparkTest.util.SparkTestUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance comparison test facing the current HTTPS (HTTP/1.1 over TCP+TLS) implementation
 * against the optional HTTP/2 implementation across Platform Threads and Virtual Threads.
 */
public class Http2PerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(Http2PerformanceTest.class);

    private static final int WARMUP_REQUESTS = 40;
    private static final int BENCHMARK_REQUESTS = 200;
    private static final int CONCURRENCY = 4;

    record BenchmarkResult(
            String scenario,
            String protocol,
            boolean virtualThreads,
            int totalRequests,
            double totalTimeMs,
            double throughputReqPerSec,
            double avgLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs
    ) {}

    @Test
    public void compareHttpsCurrentImplVsHttp2Performance() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();

        // 1. Current Implementation: HTTPS (HTTP/1.1 over TLS) + Platform Threads
        results.add(runBenchmark("HTTPS (Current Impl - Platform Threads)", false, false));

        // 2. Current Implementation + Virtual Threads: HTTPS (HTTP/1.1 over TLS) + Virtual Threads
        results.add(runBenchmark("HTTPS (Current Impl - Virtual Threads)", false, true));

        // 3. Optional HTTP/2 + Platform Threads
        results.add(runBenchmark("HTTP/2 (Platform Threads)", true, false));

        // 4. Optional HTTP/2 + Virtual Threads
        results.add(runBenchmark("HTTP/2 (Virtual Threads)", true, true));

        StringBuilder report = new StringBuilder("\n");
        report.append("========================================================================================================================\n");
        report.append(String.format("%-42s | %-8s | %-10s | %-12s | %-9s | %-9s | %-9s | %-9s%n",
                "Scenario", "Protocol", "Total (ms)", "Req/sec", "Avg (ms)", "p50 (ms)", "p95 (ms)", "p99 (ms)"));
        report.append("------------------------------------------------------------------------------------------------------------------------\n");
        for (BenchmarkResult r : results) {
            report.append(String.format("%-42s | %-8s | %10.2f | %12.2f | %9.2f | %9.2f | %9.2f | %9.2f%n",
                    r.scenario(),
                    r.protocol(),
                    r.totalTimeMs(),
                    r.throughputReqPerSec(),
                    r.avgLatencyMs(),
                    r.p50LatencyMs(),
                    r.p95LatencyMs(),
                    r.p99LatencyMs()));
            assertEquals(BENCHMARK_REQUESTS, r.totalRequests());
            assertTrue(r.throughputReqPerSec() > 0, "Throughput must be positive for " + r.scenario());
        }
        report.append("========================================================================================================================\n");
        LOG.info(report.toString());
        System.out.println(report);
    }

    private BenchmarkResult runBenchmark(String scenarioName, boolean useHttp2, boolean useVirtualThreads) throws Exception {
        Service service = Service.ignite();
        service.ipAddress("localhost");
        service.port(0);
        service.secure(
                SparkTestUtil.getKeyStoreLocation(),
                SparkTestUtil.getKeystorePassword(),
                null,
                null
        );
        service.useVirtualThreads(useVirtualThreads);

        if (useHttp2) {
            service.useHttp2();
        }

        String payload = "spark-jakarta-benchmark-payload-".repeat(16);

        try {
            service.post("/bench", (req, res) -> {
                res.header("X-Protocol", req.protocol());
                res.header("X-Virtual", String.valueOf(Thread.currentThread().isVirtual()));
                return req.body();
            });
            service.awaitInitialization();

            int port = service.port();
            String url = "https://localhost:" + port + "/bench";

            try (final HttpClient client = createJettyClient(useHttp2)) {
                client.start();

                // Warmup phase
                for (int i = 0; i < WARMUP_REQUESTS; i++) {
                    ContentResponse warmupResp = client.POST(url)
                            .body(new StringRequestContent(payload))
                            .send();
                    assertEquals(200, warmupResp.getStatus());
                }

                // Concurrent benchmark phase with bounded worker concurrency
                long[] latenciesNanos = new long[BENCHMARK_REQUESTS];
                String expectedProtocol = useHttp2 ? "HTTP/2.0" : "HTTP/1.1";
                AtomicInteger counter = new AtomicInteger(0);

                try (final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY)) {
                    long startWallNanos = System.nanoTime();

                    CompletableFuture.allOf(IntStream.range(0, CONCURRENCY).mapToObj(w -> CompletableFuture.runAsync(() -> {
                        int idx;
                        while ((idx = counter.getAndIncrement()) < BENCHMARK_REQUESTS) {
                            long reqStart = System.nanoTime();
                            try {
                                ContentResponse response = client.POST(url)
                                    .body(new StringRequestContent(payload))
                                    .timeout(15, TimeUnit.SECONDS)
                                    .send();
                                latenciesNanos[idx] = System.nanoTime() - reqStart;
                                assertEquals(200, response.getStatus());
                                assertEquals(expectedProtocol, response.getHeaders().get("X-Protocol"));
                                assertEquals(String.valueOf(useVirtualThreads), response.getHeaders().get("X-Virtual"));
                            } catch (Exception e) {
                                throw new RuntimeException(
                                    "Benchmark request failed in " + scenarioName
                                        + ", request index: " + idx
                                        + ", elapsed time: "
                                        + TimeUnit.NANOSECONDS.toMillis(
                                        System.nanoTime() - reqStart
                                    ) + " ms",
                                    e
                                );
                            }
                        }
                    }, executor)).toArray(CompletableFuture[]::new)).join();
                    long totalWallNanos = System.nanoTime() - startWallNanos;

                    Arrays.sort(latenciesNanos);
                    double totalTimeMs = totalWallNanos / 1_000_000.0;
                    double throughput = (BENCHMARK_REQUESTS * 1000.0) / totalTimeMs;
                    double avgMs = Arrays.stream(latenciesNanos).average().orElse(0.0) / 1_000_000.0;
                    double p50Ms = latenciesNanos[(int) (BENCHMARK_REQUESTS * 0.50)] / 1_000_000.0;
                    double p95Ms = latenciesNanos[(int) (BENCHMARK_REQUESTS * 0.95)] / 1_000_000.0;
                    double p99Ms = latenciesNanos[(int) (BENCHMARK_REQUESTS * 0.99)] / 1_000_000.0;

                    return new BenchmarkResult(
                            scenarioName,
                            expectedProtocol,
                            useVirtualThreads,
                            BENCHMARK_REQUESTS,
                            totalTimeMs,
                            throughput,
                            avgMs,
                            p50Ms,
                            p95Ms,
                            p99Ms
                    );
                }
            }
        } finally {
            service.stop();
            service.awaitStop();
        }
    }

    private HttpClient createJettyClient(boolean useHttp2) {
        SslContextFactory.Client sslClient = new SslContextFactory.Client(true);
        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslClient);

        if (useHttp2) {
            final HTTP2Client http2Client =
                new HTTP2Client(clientConnector);
            HttpClientTransportOverHTTP2 transport = new HttpClientTransportOverHTTP2(http2Client);
            return new HttpClient(transport);
        } else {
            HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP(clientConnector);
            HttpClient client = new HttpClient(transport);
            client.setMaxConnectionsPerDestination(CONCURRENCY);
            return client;
        }
    }
}
