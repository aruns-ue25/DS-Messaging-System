package com.dsmessaging;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;

public class RaftSimulation {

    private static void sendToLeader(MessagingSystem system, Message msg) {
        for (ServerNode server : system.getServers()) {
            if (server.getRaftNode().getState() == com.dsmessaging.raft.NodeState.LEADER) {
                System.out.println("Routing message to current LEADER: " + server.getServerId());
                system.sendMessageToServer(server.getServerId(), msg);
                return;
            }
        }
        System.out.println("No leader found for message: " + msg.getMessageId());
    }

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

        System.out.println("\n--- Sending Message to Current Leader ---");
        Message msg1 = new Message("msg-1", "Client", "System", "Hello Raft!", System.currentTimeMillis());
        sendToLeader(system, msg1);

        Thread.sleep(1000);

        System.out.println("\n--- Simulating Node Crash (Deactivating Server-1) ---");
        s1.deactivate();

        // Wait for potential new election
        Thread.sleep(1500);

        System.out.println("\n--- Sending another message to Current Leader ---");
        Message msg2 = new Message("msg-2", "Client", "System", "Testing second message!", System.currentTimeMillis());
        sendToLeader(system, msg2);

        Thread.sleep(1000);

        System.out.println("\n--- Recovering Server-1 ---");
        s1.activate();

        // Give time for catch up log replication
        Thread.sleep(2000);

        System.out.println("\n--- Final State ---");
        system.displayAllServerMessages();

        System.exit(0);
    }
}
