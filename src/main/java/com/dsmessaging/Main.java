package com.dsmessaging;

import com.dsmessaging.model.Message;
import com.dsmessaging.service.MessageService;

public class Main {
    public static void main(String[] args) {

        MessageService messageService = new MessageService();

        Message message1 = new Message(
                "msg-001",
                "Alice",
                "Bob",
                "Hello Bob!",
                System.currentTimeMillis()
        );

        messageService.sendMessage(message1);

        System.out.println("\nAll messages:");
        for (Message message : messageService.getAllMessages()) {
            System.out.println(message);
        }
    }
}
