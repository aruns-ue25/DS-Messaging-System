package com.dsmessaging;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;

public class Main {
    public static void main(String[] args) {

        // Create 3 server nodes
        ServerNode server1 = new ServerNode("Server-1");
        ServerNode server2 = new ServerNode("Server-2");
        ServerNode server3 = new ServerNode("Server-3");

        // Create a sample message
        Message message1 = new Message(
                "msg-001",
                "Alice",
                "Bob",
                "Hello Bob!",
                System.currentTimeMillis()
        );

        // Send message to Server-1
        server1.receiveMessage(message1);

        // Display messages stored in each server
        server1.displayMessages();
        server2.displayMessages();
        server3.displayMessages();
    }
}