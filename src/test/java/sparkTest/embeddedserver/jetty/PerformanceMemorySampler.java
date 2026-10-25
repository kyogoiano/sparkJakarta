package sparkTest.embeddedserver.jetty;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PerformanceMemorySampler implements AutoCloseable {

    private static final ObjectName DIAGNOSTIC_COMMAND;
    private static final Pattern NMT_TOTAL_COMMITTED =
            Pattern.compile("Total: reserved=(\\d+)KB, committed=(\\d+)KB");
    private static final Pattern NMT_HEAP_COMMITTED =
            Pattern.compile("Java Heap \\(reserved=(\\d+)KB, committed=(\\d+)KB\\)");
    private static final Path PROC_STATUS = Path.of("/proc/self/status");

    static {
        try {
            DIAGNOSTIC_COMMAND = new ObjectName("com.sun.management:type=DiagnosticCommand");
        } catch (JMException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private final Runtime runtime = Runtime.getRuntime();
    private final long baselineUsedBytes;
    private final AtomicLong peakUsedBytes = new AtomicLong();
    private final long baselineNativeCommittedBytes;
    private final AtomicLong peakNativeCommittedBytes = new AtomicLong();
    private final boolean residentSetAvailable = Files.isReadable(PROC_STATUS);
    private final long baselineResidentSetBytes;
    private final AtomicLong peakResidentSetBytes = new AtomicLong();
    private final AtomicReference<Exception> samplingFailure = new AtomicReference<>();
    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "performance-memory-sampler");
        thread.setDaemon(true);
        return thread;
    });

    PerformanceMemorySampler() {
        baselineUsedBytes = usedHeapBytes();
        baselineNativeCommittedBytes = nativeCommittedBytes();
        baselineResidentSetBytes = readBaselineResidentSetBytes();
        sample();
        sampleNativeMemory();
        sampler.scheduleAtFixedRate(this::sample, 0, 5, TimeUnit.MILLISECONDS);
        sampler.scheduleAtFixedRate(this::sampleNativeMemory, 0, 20, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the sampled peak heap increase over the baseline. This is an approximate
     * process-wide heap metric, not per-request allocation or retained memory.
     */
    long peakHeapIncreaseBytes() {
        sample();
        return Math.max(0, peakUsedBytes.get() - baselineUsedBytes);
    }

    /**
     * Returns sampled NMT committed memory excluding the Java heap. This is JVM-tracked
     * native memory, not total process memory.
     */
    long peakNativeCommittedIncreaseBytes() {
        throwIfSamplingFailed();
        sampleNativeMemory();
        throwIfSamplingFailed();
        return Math.max(0, peakNativeCommittedBytes.get() - baselineNativeCommittedBytes);
    }

    /**
     * Returns sampled Linux process RSS growth, or -1 when /proc is unavailable.
     */
    long peakResidentSetIncreaseBytes() {
        throwIfSamplingFailed();
        sample();
        throwIfSamplingFailed();
        return residentSetAvailable
                ? Math.max(0, peakResidentSetBytes.get() - baselineResidentSetBytes)
                : -1;
    }

    private long usedHeapBytes() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void sample() {
        peakUsedBytes.accumulateAndGet(usedHeapBytes(), Math::max);
        if (residentSetAvailable) {
            try {
                peakResidentSetBytes.accumulateAndGet(residentSetBytes(), Math::max);
            } catch (IOException failure) {
                samplingFailure.compareAndSet(null, failure);
            }
        }
    }

    private void sampleNativeMemory() {
        try {
            peakNativeCommittedBytes.accumulateAndGet(nativeCommittedBytes(), Math::max);
        } catch (IllegalStateException failure) {
            samplingFailure.compareAndSet(null, failure);
        }
    }

    private long nativeCommittedBytes() {
        try {
            String output = (String) mBeanServer.invoke(
                    DIAGNOSTIC_COMMAND,
                    "vmNativeMemory",
                    new Object[]{new String[]{"summary"}},
                    new String[]{"[Ljava.lang.String;"});
            Matcher totalMatcher = NMT_TOTAL_COMMITTED.matcher(output);
            Matcher heapMatcher = NMT_HEAP_COMMITTED.matcher(output);
            if (!totalMatcher.find() || !heapMatcher.find()) {
                throw new IllegalStateException(
                        "Native Memory Tracking is unavailable; run tests with -XX:NativeMemoryTracking=summary");
            }
            long totalCommittedBytes = Long.parseLong(totalMatcher.group(1)) * 1024;
            long heapCommittedBytes = Long.parseLong(heapMatcher.group(2)) * 1024;
            return totalCommittedBytes - heapCommittedBytes;
        } catch (JMException failure) {
            throw new IllegalStateException("Could not query Native Memory Tracking", failure);
        }
    }

    private long residentSetBytes() throws IOException {
        for (String line : Files.readAllLines(PROC_STATUS)) {
            if (line.startsWith("VmRSS:")) {
                String[] fields = line.trim().split("\\s+");
                return Long.parseLong(fields[1]) * 1024;
            }
        }
        throw new IOException("VmRSS was not present in /proc/self/status");
    }

    private long readBaselineResidentSetBytes() {
        if (!residentSetAvailable) {
            return -1;
        }
        try {
            return residentSetBytes();
        } catch (IOException failure) {
            samplingFailure.set(failure);
            return -1;
        }
    }

    private void throwIfSamplingFailed() {
        Exception failure = samplingFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Could not sample process memory", failure);
        }
    }

    @Override
    public void close() {
        sampler.shutdownNow();
    }
}
