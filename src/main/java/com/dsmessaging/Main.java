package com.dsmessaging;

import com.dsmessaging.service.MessagingSystem;
import com.dsmessaging.ui.UIServer;
import com.dsmessaging.server.ServerNode;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("DS MESSAGING SYSTEM: INTERACTIVE DASHBOARD (V4.0)");
        System.out.println("==================================================\n");

        // 1. Initialize the Cluster
        MessagingSystem cluster = new MessagingSystem();
        cluster.addServer("Server-1");
        cluster.addServer("Server-2");
        cluster.addServer("Server-3");

        // 2. Initialize Raft Consensus on all nodes
        for (ServerNode node : cluster.getServers()) {
            node.getRaftNode().setMessagingSystem(cluster);
            node.getRaftNode().start();
        }

        System.out.println("Cluster initialized with 3 nodes.");
        System.out.println("Raft consensus started.");

        // 3. Start the UI Server
        try {
            int uiPort = 9090;
            UIServer uiServer = new UIServer(uiPort, cluster);
            uiServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start UI Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
