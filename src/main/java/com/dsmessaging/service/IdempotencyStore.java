package com.dsmessaging.service;

import com.dsmessaging.model.IdempotencyKey;
import com.dsmessaging.model.IdempotencyRecord;
import com.dsmessaging.model.MessageStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class IdempotencyStore {
    private final String nodeId;

    public IdempotencyStore(String nodeId) {
        this.nodeId = nodeId;
    }

    public IdempotencyRecord getRecord(IdempotencyKey key) {
        String sql = "SELECT message_id, commit_version, final_status, created_at " +
                     "FROM idempotency_records " +
                     "WHERE conversation_id = ? AND sender_id = ? AND client_request_id = ? AND node_id = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, key.getConversationId());
            stmt.setString(2, key.getSenderId());
            stmt.setString(3, key.getClientRequestId());
            stmt.setString(4, nodeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new IdempotencyRecord(
                        rs.getString("message_id"),
                        rs.getInt("commit_version"),
                        MessageStatus.valueOf(rs.getString("final_status")),
                        rs.getLong("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error checking idempotency record: " + e.getMessage());
        }
        return null;
    }

    public void putRecord(IdempotencyKey key, IdempotencyRecord record) {
        String sql = "INSERT INTO idempotency_records (conversation_id, sender_id, client_request_id, node_id, message_id, commit_version, final_status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE message_id = VALUES(message_id), commit_version = VALUES(commit_version), final_status = VALUES(final_status)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, key.getConversationId());
            stmt.setString(2, key.getSenderId());
            stmt.setString(3, key.getClientRequestId());
            stmt.setString(4, nodeId);
            stmt.setString(5, record.getMessageId());
            stmt.setInt(6, record.getCommitVersion());
            stmt.setString(7, record.getFinalStatus() != null ? record.getFinalStatus().name() : null);
            stmt.setLong(8, record.getCreatedAt());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database error saving idempotency record: " + e.getMessage());
        }
    }
    
    public int getSize() {
        String sql = "SELECT COUNT(*) FROM idempotency_records WHERE node_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nodeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting idempotency size: " + e.getMessage());
        }
        return 0;
    }

    public java.util.Map<IdempotencyKey, IdempotencyRecord> getStore() {
        java.util.Map<IdempotencyKey, IdempotencyRecord> map = new java.util.concurrent.ConcurrentHashMap<>();
        String sql = "SELECT * FROM idempotency_records WHERE node_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nodeId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    IdempotencyKey key = new IdempotencyKey(rs.getString("conversation_id"), rs.getString("sender_id"), rs.getString("client_request_id"));
                    IdempotencyRecord rec = new IdempotencyRecord(rs.getString("message_id"), rs.getInt("commit_version"), MessageStatus.valueOf(rs.getString("final_status")), rs.getLong("created_at"));
                    map.put(key, rec);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error syncing idempotency store: " + e.getMessage());
        }
        return map;
    }
}
