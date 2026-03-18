package com.dsmessaging;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;

public class Main {
    public static void main(String[] args) {

        // Create the distributed messaging system
        MessagingSystem messagingSystem = new MessagingSystem();

        // Create 3 server nodes
        ServerNode server1 = new ServerNode("Server-1");
        ServerNode server2 = new ServerNode("Server-2");
        ServerNode server3 = new ServerNode("Server-3");

        // Add servers to the system
        messagingSystem.addServer(server1);
        messagingSystem.addServer(server2);
        messagingSystem.addServer(server3);

        // Create sample messages
        Message message1 = new Message(
                "msg-001",
                "Alice",
                "Bob",
                "Hello Bob!",
                System.currentTimeMillis()
        );

        Message message2 = new Message(
                "msg-002",
                "Bob",
                "Alice",
                "Hi Alice!",
                System.currentTimeMillis()
        );

        // Send messages through the messaging system
        messagingSystem.sendMessageToServer("Server-1", message1);
        messagingSystem.sendMessageToServer("Server-2", message2);

        // Display all stored messages in all servers
        messagingSystem.displayAllServerMessages();
    }
}
