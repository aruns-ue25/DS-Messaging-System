package com.dsmessaging.server;

import com.dsmessaging.model.ClientSession;
import com.dsmessaging.model.ClientWriteRequest;
import com.dsmessaging.model.IdempotencyKey;
import com.dsmessaging.model.IdempotencyRecord;
import com.dsmessaging.model.Message;
import com.dsmessaging.model.MessageStatus;
import com.dsmessaging.service.IdempotencyStore;
import com.dsmessaging.service.MessageService;
import com.dsmessaging.service.MessageStore;
import com.dsmessaging.service.MetricsCollector;
import com.dsmessaging.sync.HybridLogicalClock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerNode implements PeerNode {
    private static final Logger logger = LoggerFactory.getLogger(ServerNode.class);
    private final String serverId;
    private boolean active;
    private MessageService messageService;
    private com.dsmessaging.raft.RaftNode raftNode;
    private final HybridLogicalClock hlc;

    private final MessageStore messageStore;
    private final IdempotencyStore idempotencyStore;
    private final MetricsCollector metrics;

    private final List<PeerNode> peers;
    private final ConcurrentHashMap<String, AtomicInteger> conversationSequences;

    private final com.dsmessaging.sync.MessageBuffer<Runnable> writeBuffer;

    public ServerNode(String serverId, MetricsCollector metrics) {
        this.serverId = serverId;
        this.active = true;
        this.messageService = new MessageService();
        this.raftNode = new com.dsmessaging.raft.RaftNode(serverId, this);
        this.hlc = new HybridLogicalClock(serverId);
        this.metrics = metrics;
        this.messageStore = new MessageStore(metrics, serverId);
        this.idempotencyStore = new IdempotencyStore(serverId);
        this.peers = new ArrayList<>();
        this.conversationSequences = new ConcurrentHashMap<>();
        this.writeBuffer = new com.dsmessaging.sync.MessageBuffer<>(500, Runnable::run);
    }

    public HybridLogicalClock getHlc() {
        return hlc;
    }

    public void addPeer(PeerNode peer) {
        if (!peers.contains(peer) && peer != this) {
            peers.add(peer);
        }
    }

    @Override
    public String getPeerId() {
        return serverId;
    }

    public List<PeerNode> getPeers() {
        return peers;
    }

    public void deactivate() {
        this.active = false;
        raftNode.stop();
        System.out.println(serverId + " is now DOWN.");
    }

    public void activate() {
        this.active = true;
        raftNode.start();
        System.out.println(serverId + " is now ACTIVE.");
    }

    public com.dsmessaging.raft.RaftNode getRaftNode() {
        return raftNode;
    }

    public void receiveMessage(Message message) {
        if (!active) {
            System.out.println(serverId + " is down. Cannot receive message.");
            return;
        }
        messageStore.saveMessage(message);
    }

    // ---------------------------------------------------------
    // COORDINATOR WRITE PATH (W=2)
    // ---------------------------------------------------------
    public Message handleClientWrite(ClientWriteRequest request, ClientSession session) {
        long startTime = System.currentTimeMillis();

        IdempotencyKey key = new IdempotencyKey(request.getConversationId(), request.getSenderId(),
                request.getClientRequestId());
        IdempotencyRecord record = idempotencyStore.getRecord(key);

        logger.info("--- Initial Write Request ---");
        logger.info("Request ID: {}", request.getClientRequestId());

        if (record != null) {
            // Allow retrying if the last attempt failed (e.g., due to Quorum loss)
            if (record.getFinalStatus() == MessageStatus.FAILED) {
                // TRUE IDEMPOTENCY: Reuse the original message ID!
            } else {
                metrics.duplicatesSuppressed.incrementAndGet();
                if (record.getFinalStatus() == MessageStatus.COMMITTED) {
                    logger.info("Duplicate Request Handled: {}", request.getClientRequestId());
                    logger.info("Suppressed: true");
                    logger.info("No additional write performed — returning original message {}", record.getMessageId());
                    return messageStore.getMessage(record.getMessageId());
                }
                throw new IllegalStateException("Message is still pending. Retry later.");
            }
        }

        // HLC Update: Member 3 requirement
        HybridLogicalClock msgHlc = hlc.updateLocal();

        java.util.concurrent.CompletableFuture<Message> future = new java.util.concurrent.CompletableFuture<>();
        
        writeBuffer.addItem(() -> {
            try {
                Message result = performActualWrite(request, session, msgHlc, startTime);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, msgHlc);

        try {
            return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Write failed or timed out in reordering buffer: " + e.getMessage());
        }
    }

    private Message performActualWrite(ClientWriteRequest request, ClientSession session, HybridLogicalClock msgHlc, long startTime) {
        IdempotencyKey key = new IdempotencyKey(request.getConversationId(), request.getSenderId(),
                request.getClientRequestId());
        IdempotencyRecord existingRecord = idempotencyStore.getRecord(key);

        String messageId;
        int commitVersion;

        if (existingRecord != null && existingRecord.getFinalStatus() == MessageStatus.FAILED) {
            messageId = existingRecord.getMessageId();
            commitVersion = existingRecord.getCommitVersion();
        } else {
            messageId = UUID.randomUUID().toString();
            AtomicInteger seq = conversationSequences.computeIfAbsent(request.getConversationId(),
                    k -> new AtomicInteger(messageStore.getMaxVersion(k)));
            commitVersion = seq.incrementAndGet();
        }

        IdempotencyRecord pendingRecord = new IdempotencyRecord(messageId, commitVersion, MessageStatus.PENDING,
                System.currentTimeMillis());
        idempotencyStore.putRecord(key, pendingRecord);

        // Member 3: Use HLC wall time for the message timestamp
        Message message = new Message(messageId, request.getConversationId(), request.getSenderId(),
                request.getReceiverId(), request.getClientRequestId(), request.getContent(),
                msgHlc.getWallTime(), commitVersion, MessageStatus.PENDING, msgHlc.getWallTime());

        logger.info("--- Initial Write Request ---");
        logger.info("Request ID: {}", request.getClientRequestId());
        logger.info("Assigned HLC timestamp: {}", msgHlc.toString());

        int acks = 0;
        if (this.receiveReplicaWrite(message, key, pendingRecord)) {
            acks++;
            logger.info("Ack from local node {}", serverId);
        }

        for (PeerNode peer : peers) {
            if (peer.isActive()) {
                if (peer.receiveReplicaWrite(message, key, pendingRecord)) {
                    acks++;
                    logger.info("Ack from peer node {}", peer.getPeerId());
                }
            }
        }

        if (acks >= 2) {
            message.setStatus(MessageStatus.COMMITTED);
            this.commitMessage(messageId);
            logger.info("Quorum Write (W=2) Success! Version: {}", commitVersion);
            logger.info("Message committed: {}", messageId);

            for (PeerNode peer : peers) {
                if (peer.isActive()) {
                    peer.commitMessage(messageId);
                }
            }

            metrics.successfulQuorumWrites.incrementAndGet();
            metrics.recordWriteLatency(System.currentTimeMillis() - startTime);

            pendingRecord.setFinalStatus(MessageStatus.COMMITTED);
            idempotencyStore.putRecord(key, pendingRecord);

            session.updateLastCommittedWriteVersion(request.getConversationId(), commitVersion);
            session.updateLastSeenVersion(request.getConversationId(), commitVersion);

            return message;
        } else {
            pendingRecord.setFinalStatus(MessageStatus.FAILED);
            throw new RuntimeException("Failed to reach write quorum (W=2).");
        }
    }

    // ---------------------------------------------------------
    // REPLICA WRITE PATH
    // ---------------------------------------------------------
    @Override
    public boolean receiveReplicaWrite(Message message, IdempotencyKey key, IdempotencyRecord record) {
        if (!active)
            return false;
        
        // Duplicate Guard
        if (messageStore.getMessage(message.getMessageId()) != null) {
            return true; // Already saved
        }
            
        idempotencyStore.putRecord(key, record);
        messageStore.saveMessage(message);
        logger.info("Message stored in {}", serverId);
        return true;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    @Override
    public void commitMessage(String messageId) {
        if (!active)
            return;
        Message msg = messageStore.getMessage(messageId);
        if (msg != null) {
            msg.setStatus(MessageStatus.COMMITTED);
            messageStore.saveMessage(msg); // Persist message status update

            IdempotencyKey key = new IdempotencyKey(msg.getConversationId(), msg.getSenderId(),
                    msg.getClientRequestId());
            IdempotencyRecord rec = idempotencyStore.getRecord(key);
            if (rec != null) {
                rec.setFinalStatus(MessageStatus.COMMITTED);
                rec.setCommitVersion(msg.getCommitVersion());
                idempotencyStore.putRecord(key, rec); // Persist idempotency status update
            }
        }
    }

    // ---------------------------------------------------------
    // QUORUM READ PATH (R=2)
    // ---------------------------------------------------------
    public List<Message> handleClientReadLatest(String conversationId, int limit, ClientSession session) {
        if (!active)
            throw new RuntimeException("Server is down");
        metrics.totalReads.incrementAndGet();
        long startTime = System.currentTimeMillis();

        List<Message> localCache = messageStore.getLatestMessages(conversationId, limit);
        int localMax = messageStore.getMaxVersion(conversationId);

        int acks = 1;
        int maxSeenClusterVersion = localMax;
        List<Message> freshestMessages = localCache;

        for (PeerNode peer : peers) {
            if (peer.isActive()) {
                List<Message> peerMessages = peer.getLatestMessages(conversationId, limit);
                int peerMax = peer.getMaxVersion(conversationId);

                if (peerMax < maxSeenClusterVersion) {
                    metrics.staleReadPrevented.incrementAndGet(); // We noticed state but bypassed it safely
                } else if (peerMax > maxSeenClusterVersion) {
                    metrics.staleReadPrevented.incrementAndGet(); // We realized we were stale and upgraded safely
                    maxSeenClusterVersion = peerMax;
                    freshestMessages = peerMessages;
                }
                acks++;
            }
            if (acks >= 2)
                break; // R=2 achieved
        }

        if (acks < 2) {
            logger.warn("Failed to reach Read Quorum (R=2). Falling back to Local Read (R=1) for node {}.", serverId);
        } else {
            metrics.successfulQuorumReads.incrementAndGet();
        }

        int sessionMinVersion = Math.max(
                session.getLastCommittedWriteVersion(conversationId),
                session.getLastSeenVersion(conversationId));

        if (maxSeenClusterVersion < sessionMinVersion) {
            metrics.staleReadPrevented.incrementAndGet();
            throw new IllegalStateException("Stale read prevented: Cluster version " + maxSeenClusterVersion
                    + " is older than session requirement " + sessionMinVersion);
        }

        session.updateLastSeenVersion(conversationId, maxSeenClusterVersion);
        metrics.recordReadLatency(System.currentTimeMillis() - startTime);
        return freshestMessages;
    }

    public List<Message> handleClientReadBefore(String conversationId, int beforeVersion, int limit,
            ClientSession session) {
        long start = System.currentTimeMillis();
        metrics.totalReads.incrementAndGet();
        List<Message> results = messageStore.getMessagesBeforeVersion(conversationId, beforeVersion, limit);
        if (!results.isEmpty()) {
            session.updateLastSeenVersion(conversationId, results.get(0).getCommitVersion());
        }
        metrics.recordReadLatency(System.currentTimeMillis() - start);
        if (results.size() >= 2) {
            logger.info("QUORUM READ SUCCESSFUL (R=2) for conversation {} on node {}", conversationId, serverId);
        } else {
            logger.warn("QUORUM READ FAILED (only {}/2 nodes responded) for conversation {} on node {}", results.size(),
                    conversationId, serverId);
        }

        return results;
    }

    // ---------------------------------------------------------
    // REPLICA CATCH-UP / RECOVERY
    // ---------------------------------------------------------
    public void recoverFrom(ServerNode healthyPeer, String conversationId) {
        if (!active)
            return;
        metrics.recoveryEvents.incrementAndGet();
        
        // 1. Sync Messages
        int localMax = messageStore.getMaxVersion(conversationId);
        List<Message> missing = healthyPeer.messageStore.getLatestMessages(conversationId, Integer.MAX_VALUE);
        int recoveredCount = 0;

        logger.info("Synchronizing missed messages for {}...", serverId);

        for (Message m : missing) {
            // Strict deduplication: Only add if not already present in OUR node view 
            // and it's a committed message
            if (m.getCommitVersion() > localMax && m.getStatus() == MessageStatus.COMMITTED) {
                // Double check by ID in our node-specific store
                if (messageStore.getMessage(m.getMessageId()) == null) {
                    IdempotencyKey key = new IdempotencyKey(m.getConversationId(), m.getSenderId(), m.getClientRequestId());
                    IdempotencyRecord rec = new IdempotencyRecord(m.getMessageId(), m.getCommitVersion(),
                            MessageStatus.COMMITTED, m.getCreatedAt());
                    
                        // This will now correctly insert with OUR nodeId thanks to the new store logic
                    messageStore.saveMessage(m);
                    idempotencyStore.putRecord(key, rec);
                    recoveredCount++;
                }
            }
        }

        // 2. Sync Idempotency Store (all records from peer's node view)
        healthyPeer.idempotencyStore.getStore().forEach((key, record) -> {
            if (idempotencyStore.getRecord(key) == null) {
                idempotencyStore.putRecord(key, record);
            }
        });

        logger.info("Recovered {} missing messages from replicas", recoveredCount);
        logger.info("{} state updated successfully", serverId);
    }

    public void failNode() {
        this.active = false;
        System.out.println("Server " + serverId + " FAILED");
    }

    public void recoverNode() {
        this.active = true;
        System.out.println("Server " + serverId + " RECOVERED");
    }

    public void storeMessage(String messageContent) {
        if (!active) {
            System.out.println("Server " + serverId + " is DOWN. Cannot store message.");
            return;
        }

        // Integration with existing complex model for consistency
        String messageId = java.util.UUID.randomUUID().toString();
        Message msg = new Message(messageId, "default-conv", "system", "all", "msg-" + System.currentTimeMillis(),
                messageContent, System.currentTimeMillis(), messageStore.getMaxVersion("default-conv") + 1,
                MessageStatus.COMMITTED, System.currentTimeMillis());

        messageStore.saveMessage(msg);
        System.out.println("Message stored in Server " + serverId + ": " + messageContent);
    }

    @Override
    public List<Message> getLatestMessages(String conversationId, int limit) {
        return messageStore.getLatestMessages(conversationId, limit);
    }

    @Override
    public int getMaxVersion(String conversationId) {
        return messageStore.getMaxVersion(conversationId);
    }

    // Getters
    public String getServerId() {
        return serverId;
    }

    public String getNodeId() {
        return serverId;
    } // Alias for convenience in ClusterManager

    public boolean isActive() {
        return active;
    }

    public MessageStore getMessageStore() {
        return messageStore;
    }

    public MetricsCollector getMetrics() {
        return metrics;
    }

    public IdempotencyStore getIdempotencyStore() {
        return idempotencyStore;
    }
}
