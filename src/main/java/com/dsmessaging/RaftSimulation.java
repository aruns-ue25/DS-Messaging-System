package com.dsmessaging;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;

public class RaftSimulation {
    public static void main(String[] args) throws InterruptedException {
        MessagingSystem system = new MessagingSystem();

        ServerNode s1 = new ServerNode("Server-1");
        ServerNode s2 = new ServerNode("Server-2");
        ServerNode s3 = new ServerNode("Server-3");

        s1.getRaftNode().setMessagingSystem(system);
        s2.getRaftNode().setMessagingSystem(system);
        s3.getRaftNode().setMessagingSystem(system);

        system.addServer(s1);
        system.addServer(s2);
        system.addServer(s3);

        System.out.println("--- Starting Nodes ---");
        s1.activate();
        s2.activate();
        s3.activate();

        // Wait for leader election
        Thread.sleep(1500);

        System.out.println("\n--- Sending Message to Server-1 (Or another node) ---");
        Message msg1 = new Message("msg-1", "Client", "System", "Hello Raft!", System.currentTimeMillis());
        // For simplicity in simulation we route to s1. If it's leader, it will
        // replicate.
        // If not, it complains it's not the leader. Real systems route to current
        // leader.
        system.sendMessageToServer("Server-1", msg1);

        Thread.sleep(1000);

        System.out.println("\n--- Simulating Node Crash (Server-1) ---");
        s1.deactivate();

        // Wait for potential new election
        Thread.sleep(1500);

        System.out.println("\n--- Sending another message to Server-2 ---");
        Message msg2 = new Message("msg-2", "Client", "System", "Recovered from failure!", System.currentTimeMillis());
        system.sendMessageToServer("Server-2", msg2);

        Thread.sleep(1000);

        System.out.println("\n--- Recovering Server-1 ---");
        s1.activate();

        // Give time for catch up log replication
        Thread.sleep(1500);

        System.out.println("\n--- Final State ---");
        system.displayAllServerMessages();

        System.exit(0);
    }
}
