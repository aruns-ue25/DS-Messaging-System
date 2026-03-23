package com.dsmessaging.service;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;

import java.util.ArrayList;
import java.util.List;

public class MessagingSystem {
    private final List<ServerNode> servers;

    public MessagingSystem() {
        this.servers = new ArrayList<>();
    }

    public void addServer(ServerNode server) {
        servers.add(server);
        System.out.println(server.getServerId() + " added to the messaging system.");
    }

    public ServerNode findServerById(String serverId) {
        for (ServerNode server : servers) {
            if (server.getServerId().equals(serverId)) {
                return server;
            }
        }
        return null;
    }

    // --- Raft RPC Routing ---

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

    public void displayAllServerMessages() {
        System.out.println("\n=== DISPLAYING ALL SERVER MESSAGES ===");
        for (ServerNode server : servers) {
            server.displayMessages();
        }
    }

    public List<ServerNode> getServers() {
        return servers;
    }
}
