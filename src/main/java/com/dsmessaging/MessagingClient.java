package com.dsmessaging;

import com.dsmessaging.grpc.*;
import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MessagingClient {
    private static final Logger logger = LoggerFactory.getLogger(MessagingClient.class);

    private final MessagingServiceGrpc.MessagingServiceBlockingStub blockingStub;
    private final HybridLogicalClock hlc;
    private final ClockSync clockSync;
    private final String nodeId;

    public MessagingClient(String host, int port, String nodeId) {
        this.nodeId = nodeId;
        this.hlc = new HybridLogicalClock(nodeId);
        this.clockSync = new ClockSync();
        
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = MessagingServiceGrpc.newBlockingStub(channel);
        
        // Start background sync
        new Thread(this::backgroundSyncLoop).start();
    }

    public void sendMessage(String content) {
        // Update HLC for local event (sending)
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
                .build();
        
        try {
            MessageResponse response = blockingStub.sendMessage(request);
            if (response.getSuccess()) {
                logger.debug("Message sent successfully");
            } else {
                logger.error("Failed to send message: {}", response.getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("RPC failed: {}", e.getMessage());
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
            SyncResponse response = blockingStub.syncClock(SyncRequest.newBuilder().setT0(t0).build());
            long t3 = System.currentTimeMillis();
            clockSync.updateOffset(t0, response.getT1(), response.getT2(), t3);
        } catch (Exception e) {
            logger.warn("Clock sync failed: {}", e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 50051;
        String nodeId = (args.length > 2) ? args[2] : "client-1";

        MessagingClient client = new MessagingClient(host, port, nodeId);
        
        // Simulating some message sending
        for (int i = 0; i < 5; i++) {
            client.sendMessage("Hello World " + i);
            Thread.sleep(1000);
        }
    }
}
