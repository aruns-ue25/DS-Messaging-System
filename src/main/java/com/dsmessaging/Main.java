package com.dsmessaging;

import com.dsmessaging.model.*;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;
import com.dsmessaging.service.MetricsCollector;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("MEMBER 1: FAULT TOLERANCE WORKFLOW SIMULATION");
        System.out.println("==================================================\n");

        MessagingSystem cluster = new MessagingSystem();
        cluster.addServer("Server-1");
        cluster.addServer("Server-2");
        cluster.addServer("Server-3");
        
        ServerNode server1 = cluster.getServer("Server-1");
        ServerNode server2 = cluster.getServer("Server-2");
        ServerNode server3 = cluster.getServer("Server-3");

        System.out.println("\n--- Step 1: Normal Messaging ---");
        cluster.sendMessage("Hello Distributed World");

        System.out.println("\n--- Step 2: Simulate Failure ---");
        server1.failNode();
        cluster.checkNodeHealth();

        System.out.println("\n--- Step 3: Message After Failure (Failover) ---");
        cluster.sendMessage("Message after failure");

        System.out.println("\n--- Step 4: Recover Node ---");
        server1.recoverNode();
        cluster.checkNodeHealth();

        System.out.println("\n--- Step 5: Message After Recovery ---");
        cluster.sendMessage("Message after recovery");

        System.out.println("\n--- Final Cluster State ---");
        System.out.println("Server 1 Messages: " + server1.getMessageStore().getStorageMessageCount());
        System.out.println("Server 2 Messages: " + server2.getMessageStore().getStorageMessageCount());
        System.out.println("Server 3 Messages: " + server3.getMessageStore().getStorageMessageCount());
        
        System.out.println("\n==================================================");
        System.out.println("FAULT TOLERANCE WORKFLOW COMPLETE");
        System.out.println("==================================================");
    }
}
