package com.dsmessaging.raft;

import com.dsmessaging.model.Message;

public class LogEntry {
    private final int term;
    private final int index;
    private final Message message;

    public LogEntry(int term, int index, Message message) {
        this.term = term;
        this.index = index;
        this.message = message;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public Message getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", index=" + index +
                ", message=" + message.getMessageId() +
                '}';
    }
}
