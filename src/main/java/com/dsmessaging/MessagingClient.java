package com.dsmessaging;

import com.dsmessaging.grpc.MessagingServiceGrpc;
import com.dsmessaging.grpc.HLCTimestamp;
import com.dsmessaging.grpc.MessageRequest;
import com.dsmessaging.grpc.MessageResponse;
import com.dsmessaging.grpc.SyncRequest;
import com.dsmessaging.grpc.SyncResponse;
import com.dsmessaging.grpc.MessagingServiceGrpc.MessagingServiceBlockingStub;
import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessagingClient {
    private static final Logger logger = LoggerFactory.getLogger(MessagingClient.class);

    private final List<MessagingServiceGrpc.MessagingServiceBlockingStub> stubs;
    private final HybridLogicalClock hlc;
    private final ClockSync clockSync;
    private final String nodeId;
    private int currentServerIndex = 0;

    public MessagingClient(List<String> serverAddresses, String nodeId) {
        this.nodeId = nodeId;
        this.hlc = new HybridLogicalClock(nodeId);
        this.clockSync = new ClockSync();
        this.stubs = new ArrayList<>();
        
        for (String addr : serverAddresses) {
            String[] parts = addr.split(":");
            ManagedChannel channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                    .usePlaintext()
                    .build();
            this.stubs.add(MessagingServiceGrpc.newBlockingStub(channel));
        }
        
        // Start background sync
        new Thread(this::backgroundSyncLoop).start();
    }

    public void sendMessage(String content) {
        // Update HLC for local event (sending) (Member 3 requirement)
        HybridLogicalClock currentHlc = hlc.updateLocal();
        
        HLCTimestamp ts = HLCTimestamp.newBuilder()
                .setWallTime(currentHlc.getWallTime())
                .setLogicalCounter(currentHlc.getLogicalCounter())
                .setNodeId(nodeId)
                .build();
        
        MessageRequest request = MessageRequest.newBuilder()
                .setSenderId(nodeId)
                .setContent(content)
                .setTimestamp(ts)
                .setConversationId("default-conv")
                .setClientRequestId(UUID.randomUUID().toString())
                .build();
        
        // Automatic Failover (Member 1 requirement)
        int attempts = 0;
        boolean success = false;
        while (attempts < stubs.size() && !success) {
            try {
                MessageResponse response = stubs.get(currentServerIndex).sendMessage(request);
                if (response.getSuccess()) {
                    logger.debug("Message sent successfully to server {}", currentServerIndex);
                    success = true;
                } else {
                    logger.error("Failed to send message: {}", response.getErrorMessage());
                    // Try next server on error
                    currentServerIndex = (currentServerIndex + 1) % stubs.size();
                    attempts++;
                }
            } catch (Exception e) {
                logger.error("RPC failed for server {}: {}", currentServerIndex, e.getMessage());
                currentServerIndex = (currentServerIndex + 1) % stubs.size();
                attempts++;
            }
        }
    }

    private void backgroundSyncLoop() {
        while (true) {
            try {
                syncClock();
                Thread.sleep(5000); // Sync every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void syncClock() {
        long t0 = System.currentTimeMillis();
        try {
            SyncResponse response = stubs.get(currentServerIndex).syncClock(SyncRequest.newBuilder().setT0(t0).build());
            long t3 = System.currentTimeMillis();
            clockSync.updateOffset(t0, response.getT1(), response.getT2(), t3);
        } catch (Exception e) {
            logger.warn("Clock sync failed: {}", e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String serverNodes = (args.length > 0) ? args[0] : "localhost:50051";
        String nodeId = (args.length > 1) ? args[1] : "client-1";

        List<String> serverList = Arrays.asList(serverNodes.split(","));
        MessagingClient client = new MessagingClient(serverList, nodeId);
        
        // Simulating some message sending
        for (int i = 0; i < 5; i++) {
            client.sendMessage("Hello World " + i);
            Thread.sleep(1000);
        }
    }
}
