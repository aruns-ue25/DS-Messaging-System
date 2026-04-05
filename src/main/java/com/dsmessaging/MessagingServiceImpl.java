package com.dsmessaging;

import com.dsmessaging.grpc.MessagingServiceGrpc;
import com.dsmessaging.grpc.HLCTimestamp;
import com.dsmessaging.grpc.MessageRequest;
import com.dsmessaging.grpc.MessageResponse;
import com.dsmessaging.grpc.ReplicaWriteRequest;
import com.dsmessaging.grpc.ReplicaWriteResponse;
import com.dsmessaging.grpc.CommitRequest;
import com.dsmessaging.grpc.CommitResponse;
import com.dsmessaging.grpc.ReadRequest;
import com.dsmessaging.grpc.ReadResponse;
import com.dsmessaging.grpc.MessageData;
import com.dsmessaging.grpc.IdempotencyData;
import com.dsmessaging.grpc.SyncRequest;
import com.dsmessaging.grpc.SyncResponse;
import com.dsmessaging.model.ClientSession;
import com.dsmessaging.model.ClientWriteRequest;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import com.dsmessaging.sync.MessageBuffer;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagingServiceImpl extends com.dsmessaging.grpc.MessagingServiceGrpc.MessagingServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MessagingServiceImpl.class);

    private final HybridLogicalClock hlc;
    private final ClockSync clockSync;
    private final ServerNode serverNode;
    private final java.util.concurrent.ConcurrentHashMap<String, com.dsmessaging.model.ClientSession> sessions;
    public MessagingServiceImpl(HybridLogicalClock hlc, ClockSync clockSync, ServerNode serverNode) {
        this.hlc = hlc;
        this.clockSync = clockSync;
        this.serverNode = serverNode;
        this.sessions = new java.util.concurrent.ConcurrentHashMap<>();
    }

    private ClientSession getOrCreateSession(String senderId) {
        return sessions.computeIfAbsent(senderId, id -> new ClientSession("sess-" + id, id));
    }

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
        HLCTimestamp ts = request.getTimestamp();
        
        // Update HLC on receive (Member 3 requirement)
        hlc.updateRemote(ts.getWallTime(), ts.getLogicalCounter());
        
        logger.info("Received message from {}: '{}' with HLC {}", 
                request.getSenderId(), request.getContent(), ts);

        try {
            // Bridge to Quorum logic (Member 2 requirement)
            ClientSession session = getOrCreateSession(request.getSenderId());
            ClientWriteRequest writeReq = new ClientWriteRequest(
                request.getConversationId(), 
                request.getSenderId(), 
                "all", 
                request.getContent(), 
                request.getClientRequestId()
            );
            
            com.dsmessaging.model.Message result = serverNode.handleClientWrite(writeReq, session);
            
            responseObserver.onNext(MessageResponse.newBuilder()
                .setSuccess(true)
                .build());
        } catch (Exception e) {
            logger.error("Error handling sendMessage: {}", e.getMessage());
            responseObserver.onNext(MessageResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void receiveReplicaWrite(ReplicaWriteRequest request, StreamObserver<ReplicaWriteResponse> responseObserver) {
        try {
            com.dsmessaging.model.Message msg = fromMessageData(request.getMessage());
            com.dsmessaging.model.IdempotencyKey key = fromIdempotencyData(request.getIdempotencyKey());
            com.dsmessaging.model.IdempotencyRecord record = new com.dsmessaging.model.IdempotencyRecord(
                msg.getMessageId(), msg.getCommitVersion(), com.dsmessaging.model.MessageStatus.valueOf(request.getMessage().getStatus()), msg.getCreatedAt());
            
            boolean success = serverNode.receiveReplicaWrite(msg, key, record);
            responseObserver.onNext(ReplicaWriteResponse.newBuilder().setSuccess(success).build());
        } catch (Exception e) {
            responseObserver.onNext(ReplicaWriteResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void commitMessage(CommitRequest request, StreamObserver<CommitResponse> responseObserver) {
        serverNode.commitMessage(request.getMessageId());
        responseObserver.onNext(CommitResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void readLatest(ReadRequest request, StreamObserver<ReadResponse> responseObserver) {
        try {
            // Using a system session for the read logic or tracking per caller
            ClientSession session = new ClientSession("read-sess", "reader"); 
            java.util.List<com.dsmessaging.model.Message> messages = serverNode.handleClientReadLatest(
                request.getConversationId(), request.getLimit(), session);
            
            ReadResponse.Builder builder = ReadResponse.newBuilder()
                .setMaxClusterVersion(serverNode.getMaxVersion(request.getConversationId()));
            
            for (com.dsmessaging.model.Message m : messages) {
                builder.addMessages(toMessageData(m));
            }
            
            responseObserver.onNext(builder.build());
        } catch (Exception e) {
            logger.error("Error in readLatest: {}", e.getMessage());
            // Return empty list or error
            responseObserver.onNext(ReadResponse.newBuilder().build());
        }
        responseObserver.onCompleted();
    }

    // Helper conversion methods
    private com.dsmessaging.model.Message fromMessageData(MessageData d) {
        return new com.dsmessaging.model.Message(
            d.getMessageId(), d.getConversationId(), d.getSenderId(), d.getReceiverId(),
            d.getClientRequestId(), d.getContent(), d.getCreatedAt(), d.getCommitVersion(),
            com.dsmessaging.model.MessageStatus.valueOf(d.getStatus()), System.currentTimeMillis()
        );
    }

    private com.dsmessaging.model.IdempotencyKey fromIdempotencyData(IdempotencyData k) {
        return new com.dsmessaging.model.IdempotencyKey(k.getConversationId(), k.getSenderId(), k.getClientRequestId());
    }

    private MessageData toMessageData(com.dsmessaging.model.Message m) {
        return MessageData.newBuilder()
            .setMessageId(m.getMessageId())
            .setConversationId(m.getConversationId())
            .setSenderId(m.getSenderId())
            .setReceiverId(m.getReceiverId())
            .setClientRequestId(m.getClientRequestId())
            .setContent(m.getContent())
            .setCreatedAt(m.getCreatedAt())
            .setCommitVersion(m.getCommitVersion())
            .setStatus(m.getStatus().toString())
            .build();
    }

    @Override
    public void syncClock(SyncRequest request, StreamObserver<SyncResponse> responseObserver) {
        long t1 = System.currentTimeMillis(); // Receive time
        long t2 = System.currentTimeMillis(); // Transmit time (simulating instantaneous)
        
        responseObserver.onNext(SyncResponse.newBuilder()
                .setT0(request.getT0())
                .setT1(t1)
                .setT2(t2)
                .build());
        responseObserver.onCompleted();
    }
}
