package com.dsmessaging.service;

import com.dsmessaging.model.Message;

import java.util.ArrayList;
import java.util.List;

public class MessageService {
    private final List<Message> messages = new ArrayList<>();

    public void sendMessage(Message message) {
        messages.add(message);
        System.out.println("Message stored: " + message);
    }

    public List<Message> getAllMessages() {
        return messages;
    }
}
