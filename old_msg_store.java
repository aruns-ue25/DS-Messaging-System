package com.dsmessaging.service;

import com.dsmessaging.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStore {
    private final MetricsCollector metrics;
    
    // Simulating "Disk" storage
    private final Map<String, List<Message>> diskMessagesByConv = new ConcurrentHashMap<>();
    private final Map<String, Message> messagesById = new ConcurrentHashMap<>();
    
    // Simulating explicitly RAM "Recent Message Cache"
    private final Map<String, List<Message>> recentCache = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 50;

    public MessageStore(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public synchronized void saveMessage(Message message) {
        // ID Guard: Never save a duplicate message ID
        if (messagesById.containsKey(message.getMessageId())) {
            return;
        }

        messagesById.put(message.getMessageId(), message);
        
        // Save to specific conversation structure
        diskMessagesByConv.computeIfAbsent(message.getConversationId(), k -> new ArrayList<>()).add(message);
        diskMessagesByConv.get(message.getConversationId()).sort((m1, m2) -> Integer.compare(m1.getCommitVersion(), m2.getCommitVersion()));
        
        // Update LRU-style Recent Cache
        List<Message> cached = recentCache.computeIfAbsent(message.getConversationId(), k -> new ArrayList<>());
        cached.add(message);
        cached.sort((m1, m2) -> Integer.compare(m1.getCommitVersion(), m2.getCommitVersion()));
        if (cached.size() > CACHE_LIMIT) {
            cached.remove(0); // Evict oldest
        }
    }

    public Message getMessage(String messageId) {
        return messagesById.get(messageId);
    }

    public int getMaxVersion(String conversationId) {
        List<Message> msgs = diskMessagesByConv.get(conversationId);
        if (msgs == null || msgs.isEmpty()) return 0;
        return msgs.get(msgs.size() - 1).getCommitVersion();
    }

    public List<Message> getLatestMessages(String conversationId, int limit) {
        metrics.cacheReads.incrementAndGet();
        List<Message> cached = recentCache.get(conversationId);
        
        List<Message> resultList = null;

        // Serve from cache if it contains enough elements, or if it has ALL the elements
        if (cached != null && cached.size() >= limit) {
            metrics.cacheHits.incrementAndGet();
            int start = cached.size() - limit;
            resultList = new ArrayList<>(cached.subList(start, cached.size()));
        } else if (cached != null && cached.size() == diskMessagesByConv.getOrDefault(conversationId, new ArrayList<>()).size()) {
            metrics.cacheHits.incrementAndGet();
            resultList = new ArrayList<>(cached);
        } else {
            // Cache miss -> Fetch from 'disk'
            metrics.cacheMisses.incrementAndGet();
            List<Message> diskMsgs = diskMessagesByConv.getOrDefault(conversationId, new ArrayList<>());
            int start = Math.max(0, diskMsgs.size() - limit);
            resultList = new ArrayList<>(diskMsgs.subList(start, diskMsgs.size()));
        }

        // Only return COMMITTED messages to prevent UI from showing failed attempts
        List<Message> filtered = new ArrayList<>();
        for (Message m : resultList) {
            if (m.getStatus() == com.dsmessaging.model.MessageStatus.COMMITTED) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    public List<Message> getMessagesBeforeVersion(String conversationId, int beforeVersion, int limit) {
        // Pagination typically bypasses the latest cache
        List<Message> msgs = diskMessagesByConv.getOrDefault(conversationId, new ArrayList<>());
        List<Message> result = new ArrayList<>();
        
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Message m = msgs.get(i);
            if (m.getCommitVersion() < beforeVersion) {
                result.add(0, m); 
                if (result.size() >= limit) break;
            }
        }
        return result;
    }
    
    public int getStorageMessageCount() {
        return messagesById.size();
    }
}
