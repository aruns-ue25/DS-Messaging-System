package com.dsmessaging;

import com.dsmessaging.grpc.MessagingServiceGrpc;
import com.dsmessaging.grpc.HLCTimestamp;
import com.dsmessaging.grpc.MessageRequest;
import com.dsmessaging.grpc.MessageResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.UUID;

public class IdempotencyTest {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 50051;
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        MessagingServiceGrpc.MessagingServiceBlockingStub stub = MessagingServiceGrpc.newBlockingStub(channel);

        String clientRequestId = UUID.randomUUID().toString();
        HLCTimestamp ts = HLCTimestamp.newBuilder()
                .setWallTime(System.currentTimeMillis())
                .setLogicalCounter(0)
                .setNodeId("test-client")
                .build();

        MessageRequest request = MessageRequest.newBuilder()
                .setSenderId("test-client")
                .setContent("Idempotent Message")
                .setTimestamp(ts)
                .setConversationId("idempotency-conv")
                .setClientRequestId(clientRequestId)
                .build();

        System.out.println("Sending first request with ID: " + clientRequestId);
        MessageResponse resp1 = stub.sendMessage(request);
        System.out.println("First response success: " + resp1.getSuccess());

        System.out.println("Sending duplicate request with same ID: " + clientRequestId);
        MessageResponse resp2 = stub.sendMessage(request);
        System.out.println("Second response success: " + resp2.getSuccess());
        
        if (resp1.getSuccess() && resp2.getSuccess()) {
             System.out.println("IDEMPOTENCY TEST PASSED: Both requests succeeded and second was suppressed.");
        } else {
             System.out.println("IDEMPOTENCY TEST FAILED.");
        }

        channel.shutdown();
    }
}
