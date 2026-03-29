package com.dsmessaging.service;

import com.dsmessaging.server.ServerNode;

import java.util.ArrayList;
import java.util.List;
import com.dsmessaging.model.Message;

public class MessagingSystem {
    private final List<ServerNode> servers;
    private final MetricsCollector globalMetrics;

    public MessagingSystem() {
        this.servers = new ArrayList<>();
        this.globalMetrics = new MetricsCollector();
    }

    public void addServer(String serverId) {
        ServerNode newServer = new ServerNode(serverId, globalMetrics);

        for (ServerNode existing : servers) {
            existing.addPeer(newServer);
            newServer.addPeer(existing);
        }

        servers.add(newServer);
        System.out.println(serverId + " added to the messaging system.");
    }

    public ServerNode getServer(String serverId) {
        for (ServerNode server : servers) {
            if (server.getServerId().equals(serverId))
                return server;
        }
        throw new IllegalArgumentException("Server not found");
    }

    public void checkNodeHealth() {
        for (ServerNode node : servers) {
            if (!node.isActive()) {
                System.out.println("ClusterManager detected failure in Server " + node.getNodeId());
            }
        }
    }

    private ServerNode getActiveNode() {
        for (ServerNode node : servers) {
            if (node.isActive()) {
                return node;
            }
        }
        return null;
    }

    // --- Raft RPC Routing ---

    private ServerNode findServerById(String id) {
        try {
            return getServer(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void broadcastRequestVote(String candidateId, com.dsmessaging.raft.RaftRPC.RequestVote request) {
        for (ServerNode node : servers) {
            if (!node.getServerId().equals(candidateId)) {
                // Simulate network jump
                new Thread(() -> {
                    com.dsmessaging.raft.RaftRPC.RequestVoteReply reply = node.getRaftNode().handleRequestVote(request);
                    if (reply != null) {
                        sendRequestVoteReply(candidateId, node.getServerId(), reply);
                    }
                }).start();
            }
        }
    }

    public void sendRequestVoteReply(String candidateId, String voterId,
            com.dsmessaging.raft.RaftRPC.RequestVoteReply reply) {
        ServerNode candidate = findServerById(candidateId);
        if (candidate != null) {
            new Thread(() -> candidate.getRaftNode().handleRequestVoteReply(voterId, reply)).start();
        }
    }

    public void sendAppendEntries(String targetId, String leaderId,
            com.dsmessaging.raft.RaftRPC.AppendEntries request) {
        ServerNode target = findServerById(targetId);
        if (target != null) {
            new Thread(() -> {
                com.dsmessaging.raft.RaftRPC.AppendEntriesReply reply = target.getRaftNode()
                        .handleAppendEntries(request);
                if (reply != null) {
                    sendAppendEntriesReply(leaderId, targetId, reply);
                }
            }).start();
        }
    }

    public void sendAppendEntriesReply(String leaderId, String followerId,
            com.dsmessaging.raft.RaftRPC.AppendEntriesReply reply) {
        ServerNode leader = findServerById(leaderId);
        if (leader != null) {
            new Thread(() -> leader.getRaftNode().handleAppendEntriesReply(followerId, reply)).start();
        }
    }

    // --- Client Routing ---

    public void sendMessageToServer(String serverId, Message message) {
        ServerNode server = findServerById(serverId);
        if (server == null) {
            System.out.println("Server " + serverId + " not found.");
            return;
        }

        // Route to raft node instead of directly storing
        server.getRaftNode().clientRequest(message);
    }

    public void sendMessage(String message) {
        ServerNode target = getActiveNode();

        if (target == null) {
            System.out.println("No active servers available!");
            return;
        }

        target.storeMessage(message);
        replicateMessage(target, message);
    }

    public void replicateMessage(ServerNode source, String message) {
        for (ServerNode node : servers) {
            if (node != source && node.isActive()) {
                node.storeMessage(message);
            }
        }
    }

    public MetricsCollector getGlobalMetrics() {
        return globalMetrics;
    }
}
