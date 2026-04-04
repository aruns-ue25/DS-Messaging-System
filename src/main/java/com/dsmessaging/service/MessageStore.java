package com.dsmessaging.service;

import com.dsmessaging.model.Message;
import com.dsmessaging.model.MessageStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStore {
    private final MetricsCollector metrics;
    
    // Simulating explicitly RAM "Recent Message Cache" (Preserved from V3.1)
    private final Map<String, List<Message>> recentCache = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 50;

    public MessageStore(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public synchronized void saveMessage(Message message) {
        String sql = "INSERT INTO messages (id, conversation_id, sender_id, receiver_id, content, commit_version, created_at, status, client_request_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE content = VALUES(content), commit_version = VALUES(commit_version), status = VALUES(status)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, message.getMessageId());
            stmt.setString(2, message.getConversationId());
            stmt.setString(3, message.getSenderId());
            stmt.setString(4, message.getReceiverId());
            stmt.setString(5, message.getContent());
            stmt.setInt(6, message.getCommitVersion());
            stmt.setLong(7, message.getCreatedAt());
            stmt.setString(8, message.getStatus() != null ? message.getStatus().name() : null);
            stmt.setString(9, message.getClientRequestId());
            
            stmt.executeUpdate();
            
            // Update LRU-style Recent Cache
            List<Message> cached = recentCache.computeIfAbsent(message.getConversationId(), k -> new ArrayList<>());
            cached.add(message);
            cached.sort((m1, m2) -> Integer.compare(m1.getCommitVersion(), m2.getCommitVersion()));
            if (cached.size() > CACHE_LIMIT) {
                cached.remove(0); // Evict oldest
            }
        } catch (SQLException e) {
            System.err.println("Database error saving message: " + e.getMessage());
        }
    }

    public Message getMessage(String messageId) {
        String sql = "SELECT * FROM messages WHERE id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToMessage(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error fetching message: " + e.getMessage());
        }
        return null;
    }

    public int getMaxVersion(String conversationId) {
        String sql = "SELECT MAX(commit_version) FROM messages WHERE conversation_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, conversationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error fetching max version: " + e.getMessage());
        }
        return 0;
    }

    public List<Message> getLatestMessages(String conversationId, int limit) {
        metrics.cacheReads.incrementAndGet();
        List<Message> cached = recentCache.get(conversationId);
        
        // Serve from cache if it contains enough elements
        if (cached != null && cached.size() >= limit) {
            metrics.cacheHits.incrementAndGet();
            int start = cached.size() - limit;
            return new ArrayList<>(cached.subList(start, cached.size()));
        }
        
        // Check if cache actually has everything in the DB
        int dbTotal = getCountByConv(conversationId);
        if (cached != null && cached.size() == dbTotal) {
            metrics.cacheHits.incrementAndGet();
            return new ArrayList<>(cached);
        }
        
        // Cache miss -> Fetch from disk
        metrics.cacheMisses.incrementAndGet();
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY commit_version DESC LIMIT ?";
        List<Message> results = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, conversationId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(0, mapRowToMessage(rs)); // Ensure it's in ascending chronological order for the client
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error fetching latest messages: " + e.getMessage());
        }
        return results;
    }

    public List<Message> getMessagesBeforeVersion(String conversationId, int beforeVersion, int limit) {
        // Pagination typically bypasses the latest cache to test DB indexes
        String sql = "SELECT * FROM messages WHERE conversation_id = ? AND commit_version < ? ORDER BY commit_version DESC LIMIT ?";
        List<Message> results = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, conversationId);
            stmt.setInt(2, beforeVersion);
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(0, mapRowToMessage(rs)); // reverse back to sequential
                }
            }
        } catch (SQLException e) {
             System.err.println("Database error fetching paginated messages: " + e.getMessage());
        }
        return results;
    }
    
    public int getStorageMessageCount() {
        String sql = "SELECT COUNT(*) FROM messages";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Database error fetching generic message count: " + e.getMessage());
        }
        return 0;
    }
    
    private int getCountByConv(String conversationId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, conversationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) { }
        return 0;
    }
    
    private Message mapRowToMessage(ResultSet rs) throws SQLException {
        Message msg = new Message(
            rs.getString("conversation_id"),
            rs.getString("sender_id"),
            rs.getString("receiver_id"),
            rs.getString("content")
        );
        msg.setMessageId(rs.getString("id"));
        msg.setCommitVersion(rs.getInt("commit_version"));
        msg.setCreatedAt(rs.getLong("created_at"));
        msg.setStatus(MessageStatus.valueOf(rs.getString("status")));
        msg.setClientRequestId(rs.getString("client_request_id"));
        return msg;
    }
}
