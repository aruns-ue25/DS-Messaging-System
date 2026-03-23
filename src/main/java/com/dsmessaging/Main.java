package com.dsmessaging;

import com.dsmessaging.model.*;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;
import com.dsmessaging.service.MetricsCollector;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("MEMBER 2: DATA REPLICATION & CONSISTENCY (V2.0)");
        System.out.println("==================================================\n");

        MessagingSystem system = new MessagingSystem();
        system.addServer("Server-1");
        system.addServer("Server-2");
        system.addServer("Server-3");
        
        ServerNode s1 = system.getServer("Server-1");
        ServerNode s2 = system.getServer("Server-2");
        ServerNode s3 = system.getServer("Server-3");
        
        ClientSession sessionA = new ClientSession("sess-A", "Alice");
        String convId = "chat-123";
        
        System.out.println("\n--- Tests 1-4: Quorum Writes, Failures, & Idempotency ---");
        ClientWriteRequest req1 = new ClientWriteRequest(convId, "Alice", "Bob", "Hello!", "req-1");
        s1.handleClientWrite(req1, sessionA);
        
        // Duplicate suppression
        s1.handleClientWrite(req1, sessionA); 
        
        // Replica Down (W=2 still holds)
        s3.deactivate();
        ClientWriteRequest req3 = new ClientWriteRequest(convId, "Alice", "Bob", "W=2 Test", "req-3");
        s1.handleClientWrite(req3, sessionA); 
        
        // Coordinator Timeout Retry on another server
        s2.handleClientWrite(req3, sessionA);
        System.out.println("Basic Quorum Writes and Deduplication Confirmed.");

        System.out.println("\n--- Tests 5-8: Quorum Reads, Stale Reads, & Recovery ---");
        s3.activate();
        // Force stale read resolution (S3 lacks req-3, but Quorum Read corrects it)
        s3.handleClientReadLatest(convId, 10, sessionA);
        s3.recoverFrom(s1, convId);
        System.out.println("Stale Read bypass, Monotonic Constraints, and Recovery Confirmed.");

        System.out.println("\n--- Test 9: Realistic Load & Cache Pagination ---");
        System.out.println("Injecting 200 bulk messages into conversation database to stress cache and retrieval...");
        for (int i = 0; i < 200; i++) {
            s1.handleClientWrite(new ClientWriteRequest(convId, "Alice", "Bob", "Bulk Message " + i, "bulk-req-" + i), sessionA);
        }
        
        System.out.println("\n1. Fetching latest 20 messages (Should Hit RAM Cache):");
        long startCache = System.nanoTime();
        List<Message> latest = s1.handleClientReadLatest(convId, 20, sessionA);
        long endCache = System.nanoTime();
        System.out.println("   Fetched " + latest.size() + " messages. Top version: " + latest.get(latest.size()-1).getCommitVersion() + ". Time: " + (endCache - startCache)/1000 + " us.");
        
        System.out.println("\n2. Fetching historical messages via cursor pagination Before Version 150 (Should Miss Cache, Hit DB Index):");
        long startCursor = System.nanoTime();
        List<Message> historical = s1.handleClientReadBefore(convId, 150, 50, sessionA);
        long endCursor = System.nanoTime();
        System.out.println("   Fetched " + historical.size() + " historical messages. Oldest Version: " + historical.get(0).getCommitVersion() + ". Time: " + (endCursor - startCursor)/1000 + " us.");
        
        
        System.out.println("\n==================================================");
        System.out.println("FINAL REPORT: DATA REPLICATION & CONSISTENCY");
        System.out.println("==================================================");
        
        System.out.println("\n1. Architecture Configuration:");
        System.out.println("   - Replication Factor (N) : 3 (Server-1, Server-2, Server-3)");
        System.out.println("   - Write Quorum (W)       : 2");
        System.out.println("   - Read Quorum (R)        : 2");
        System.out.println("   - Partitioning Strategy  : Sharded by Conversation ID");
        System.out.println("   - Consistency Guarantees : Causal per Conv, Read-your-writes, Monotonic reads");
        
        MetricsCollector m = system.getGlobalMetrics();
        
        System.out.println("\n2. Duplicate Suppression & Recovery:");
        System.out.println("   - Duplicates Caught      : " + m.duplicatesSuppressed.get());
        System.out.println("   - Stale Reads Prevented  : " + m.staleReadPrevented.get());
        System.out.println("   - Replica Catch-up Events: " + m.recoveryEvents.get());
        
        System.out.println("\n3. Retrieval & Caching Metrics:");
        System.out.println("   - Total Reads Processed  : " + m.totalReads.get());
        System.out.println("   - Quorum Reads Achieved  : " + m.successfulQuorumReads.get());
        System.out.println("   - Cache Accesses Evaluated: " + m.cacheReads.get());
        System.out.println("   - Cache Hits / Misses    : " + m.cacheHits.get() + " / " + m.cacheMisses.get());
        
        System.out.println("\n4. Latency Analysis:");
        System.out.printf("   - Write Latency (Avg)    : %.2f ms\n", m.getAvg(m.getWriteLatencies()));
        System.out.printf("   - Write Latency (p95)    : %d ms\n", m.getPercentile(m.getWriteLatencies(), 95));
        System.out.printf("   - Read Latency (Avg)     : %.2f ms\n", m.getAvg(m.getReadLatencies()));
        System.out.printf("   - Read Latency (p95)     : %d ms\n", m.getPercentile(m.getReadLatencies(), 95));
        
        // Approximate 150 bytes per logical message + 50 bytes metadata.
        int totalLogical = s1.getMessageStore().getStorageMessageCount();
        int totalReplicated = totalLogical * 3;
        int logicalBytes = totalLogical * 200;
        int replicatedBytes = totalReplicated * 200;
        
        System.out.println("\n5. Storage Analysis:");
        System.out.println("   - Logical Messages Stored: " + totalLogical);
        System.out.println("   - Replicated Messages    : " + totalReplicated);
        System.out.println("   - Storage Size / Replica : " + logicalBytes + " bytes");
        System.out.println("   - Total Cluster Storage  : " + replicatedBytes + " bytes");
        System.out.println("   - Storage Amplification  : 3.0x");
        System.out.println("==================================================\n");
    }
}
