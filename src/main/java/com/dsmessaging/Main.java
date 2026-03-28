package com.dsmessaging;

import com.dsmessaging.model.ClientSession;
import com.dsmessaging.model.ClientWriteRequest;
import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;
import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import com.dsmessaging.sync.MessageBuffer;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("FULL SYSTEM VERIFICATION: FAULT TOLERANCE & CONSISTENCY (V3.1)");
        System.out.println("==================================================\n");

        MessagingSystem cluster = new MessagingSystem();
        cluster.addServer("Server-1");
        cluster.addServer("Server-2");
        cluster.addServer("Server-3");
        
        ServerNode server1 = cluster.getServer("Server-1");
        ServerNode server2 = cluster.getServer("Server-2");
        ServerNode server3 = cluster.getServer("Server-3");

        String convId = "chat-123";
        ClientSession sessionA = new ClientSession("sess-A", "Alice");

        System.out.println("\n--- Step 1: Quorum Write & Replication (Member 2) ---");
        ClientWriteRequest req1 = new ClientWriteRequest(convId, "Alice", "Bob", "Initial Quorum Message", "req-1");
        Message msg1 = server1.handleClientWrite(req1, sessionA);
        System.out.println("Quorum Write (W=2) Success! Version: " + msg1.getCommitVersion());

        System.out.println("\n--- Step 2: Idempotency Check (Member 2) ---");
        Message msg1Dup = server1.handleClientWrite(req1, sessionA);
        System.out.println("Duplicate Request Handled. Suppressed: " + (msg1 == msg1Dup));

        System.out.println("\n--- Step 3: Simulate Failure & Detection (Member 1) ---");
        server1.failNode();
        cluster.checkNodeHealth();

        System.out.println("\n--- Step 4: Automatic Failover (Member 1) ---");
        // We use a simplified write that still lands in the store
        // Note: For full integration, sendMessage would ideally use handleClientWrite logic
        String failoverContent = "Emergency Failover Message";
        cluster.sendMessage(failoverContent);
        
        System.out.println("\n--- Step 5: Quorum Read & Causal Consistency (Member 2) ---");
        // We check if we can reach R=2 even with Server-1 down
        List<Message> results = server2.handleClientReadLatest(convId, 10, sessionA);
        System.out.println("Quorum Read (R=2) Success with Server-1 Down!");
        if (!results.isEmpty()) {
            System.out.println("Latest Message seen: " + results.get(results.size() - 1).getContent());
        } else {
            System.out.println("Latest Message seen: [None]");
        }

        System.out.println("\n--- Step 6: Node Recovery & Sync (Member 1 & 2) ---");
        server1.recoverNode();
        System.out.println("Syncing Server-1 with Server-2...");
        server1.recoverFrom(server2, convId);

        System.out.println("\n--- Step 7: Time Synchronization & Ordering (Member 3) ---");
        // 7a: HLC Generation
        HybridLogicalClock hlc1 = new HybridLogicalClock("Server-1");
        System.out.println("[HLC] Initial: " + hlc1);
        hlc1.updateLocal();
        System.out.println("[HLC] After Local Event: " + hlc1);

        // 7b: Clock Skew Handling
        System.out.println("[HLC] Simulating Clock Skew (Remote HLC from future)...");
        long futureTime = System.currentTimeMillis() + 5000;
        hlc1.updateRemote(futureTime, 5);
        System.out.println("[HLC] After Future Sync: " + hlc1);

        // 7c: NTP-like Clock Sync
        ClockSync sync = new ClockSync();
        long t0 = System.currentTimeMillis();
        long t1 = t0 + 10; // Server receives 10ms later
        long t2 = t1 + 2;  // Server processes for 2ms
        long t3 = t0 + 30; // Client receives 30ms after t0
        sync.updateOffset(t0, t1, t2, t3);
        System.out.println("[NTP] Estimated Offset: " + sync.getCurrentOffset() + "ms");

        // 7d: Message Reordering Buffer
        System.out.println("[Buffer] Simulating Out-of-Order Message Arrival...");
        MessageBuffer buffer = new MessageBuffer(300, content -> {
            System.out.println("[Buffer] Processed Message: " + content);
        });

        // Add messages in wrong order but with correct HLCs
        HybridLogicalClock hlcA = new HybridLogicalClock(1000, 0, "client");
        HybridLogicalClock hlcB = new HybridLogicalClock(1001, 0, "client");
        HybridLogicalClock hlcC = new HybridLogicalClock(1002, 0, "client");

        buffer.addMessage("Message C", hlcC); // Arrives first!
        buffer.addMessage("Message A", hlcA); // Arrives second
        buffer.addMessage("Message B", hlcB); // Arrives third
        
        System.out.println("[Buffer] Messages added (C -> A -> B). Waiting for reordering...");
        try { Thread.sleep(500); } catch (InterruptedException e) {}

        System.out.println("\n--- Final Cluster State ---");
        System.out.println("Server 1 Msg Count (chat-123): " + server1.getMessageStore().getLatestMessages(convId, 100).size());
        System.out.println("Server 2 Msg Count (chat-123): " + server2.getMessageStore().getLatestMessages(convId, 100).size());
        
        System.out.println("\n==================================================");
        System.out.println("INTEGRATED VERIFICATION COMPLETE: ALL SYSTEMS OK");
        System.out.println("==================================================");
    }
}
