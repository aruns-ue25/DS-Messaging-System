package com.dsmessaging.server;

import com.dsmessaging.grpc.*;
import com.dsmessaging.model.IdempotencyKey;
import com.dsmessaging.model.IdempotencyRecord;
import com.dsmessaging.model.Message;
import com.dsmessaging.model.MessageStatus;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcPeerNode implements PeerNode {
    private static final Logger logger = LoggerFactory.getLogger(GrpcPeerNode.class);
    private final String peerId;
    private final MessagingServiceGrpc.MessagingServiceBlockingStub blockingStub;
    private final ManagedChannel channel;

    public GrpcPeerNode(String peerId, String host, int port) {
        this.peerId = peerId;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = MessagingServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public String getPeerId() {
        return peerId;
    }

    @Override
    public boolean isActive() {
        // Simple health check via gRPC (could be more sophisticated)
        try {
            // We can use a tiny syncClock call or another RPC to check health
            blockingStub.syncClock(SyncRequest.newBuilder().setT0(System.currentTimeMillis()).build());
            return true;
        } catch (Exception e) {
            logger.warn("Health check failed for peer {}: {}", peerId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean receiveReplicaWrite(Message message, IdempotencyKey key, IdempotencyRecord record) {
        ReplicaWriteRequest request = ReplicaWriteRequest.newBuilder()
                .setMessage(toMessageData(message))
                .setIdempotencyKey(toIdempotencyData(key))
                .build();
        try {
            ReplicaWriteResponse response = blockingStub.receiveReplicaWrite(request);
            return response.getSuccess();
        } catch (Exception e) {
            logger.error("Replica write failed for peer {}: {}", peerId, e.getMessage());
            return false;
        }
    }

    @Override
    public void commitMessage(String messageId) {
        CommitRequest request = CommitRequest.newBuilder().setMessageId(messageId).build();
        try {
            blockingStub.commitMessage(request);
        } catch (Exception e) {
            // Log failure or handle retry
        }
    }

    @Override
    public List<Message> getLatestMessages(String conversationId, int limit) {
        ReadRequest request = ReadRequest.newBuilder()
                .setConversationId(conversationId)
                .setLimit(limit)
                .build();
        try {
            ReadResponse response = blockingStub.readLatest(request);
            List<Message> messages = new ArrayList<>();
            for (MessageData data : response.getMessagesList()) {
                messages.add(fromMessageData(data));
            }
            return messages;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public int getMaxVersion(String conversationId) {
        ReadRequest request = ReadRequest.newBuilder()
                .setConversationId(conversationId)
                .setLimit(1) // Just to get the max version from the response metadata
                .build();
        try {
            ReadResponse response = blockingStub.readLatest(request);
            return response.getMaxClusterVersion();
        } catch (Exception e) {
            return 0;
        }
    }

    private MessageData toMessageData(Message m) {
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

    private Message fromMessageData(MessageData d) {
        return new Message(
                d.getMessageId(),
                d.getConversationId(),
                d.getSenderId(),
                d.getReceiverId(),
                d.getClientRequestId(),
                d.getContent(),
                d.getCreatedAt(),
                d.getCommitVersion(),
                MessageStatus.valueOf(d.getStatus()),
                System.currentTimeMillis()
        );
    }

    private IdempotencyData toIdempotencyData(IdempotencyKey k) {
        return IdempotencyData.newBuilder()
                .setConversationId(k.getConversationId())
                .setSenderId(k.getSenderId())
                .setClientRequestId(k.getClientRequestId())
                .build();
    }

    public void shutdown() {
        channel.shutdown();
    }
}
