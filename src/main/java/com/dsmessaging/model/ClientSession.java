package com.dsmessaging.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSession {
    private final String sessionId;
    private final String clientId;
    private final Map<String, Integer> lastSeenVersions = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastCommittedWriteVersions = new ConcurrentHashMap<>();

    public ClientSession(String sessionId, String clientId) {
        this.sessionId = sessionId;
        this.clientId = clientId;
    }

    public String getSessionId() { return sessionId; }
    public String getClientId() { return clientId; }

    public int getLastSeenVersion(String conversationId) {
        return lastSeenVersions.getOrDefault(conversationId, 0);
    }

    public void updateLastSeenVersion(String conversationId, int version) {
        lastSeenVersions.merge(conversationId, version, Math::max);
    }

    public int getLastCommittedWriteVersion(String conversationId) {
        return lastCommittedWriteVersions.getOrDefault(conversationId, 0);
    }

    public void updateLastCommittedWriteVersion(String conversationId, int version) {
        lastCommittedWriteVersions.merge(conversationId, version, Math::max);
    }
}
