package com.dsmessaging.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    public final AtomicInteger duplicatesSuppressed = new AtomicInteger();
    public final AtomicInteger successfulQuorumWrites = new AtomicInteger();
    public final AtomicInteger successfulQuorumReads = new AtomicInteger();
    public final AtomicInteger totalReads = new AtomicInteger();
    public final AtomicInteger cacheHits = new AtomicInteger();
    public final AtomicInteger cacheMisses = new AtomicInteger();
    public final AtomicInteger cacheReads = new AtomicInteger();
    public final AtomicInteger staleReadPrevented = new AtomicInteger();
    public final AtomicInteger recoveryEvents = new AtomicInteger();

    private final List<Long> writeLatencies = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> readLatencies = Collections.synchronizedList(new ArrayList<>());

    public void recordWriteLatency(long latencyMs) {
        writeLatencies.add(latencyMs);
    }
    
    public void recordReadLatency(long latencyMs) {
        readLatencies.add(latencyMs);
    }

    public double getAvg(List<Long> latencies) {
        if (latencies.isEmpty()) return 0;
        return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public long getPercentile(List<Long> latencies, double percentile) {
        if (latencies.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
    
    public List<Long> getWriteLatencies() { return writeLatencies; }
    public List<Long> getReadLatencies() { return readLatencies; }
}
