package com.dsmessaging.server;

import com.dsmessaging.model.IdempotencyKey;
import com.dsmessaging.model.IdempotencyRecord;
import com.dsmessaging.model.Message;
import java.util.List;

public interface PeerNode {
    String getPeerId();
    boolean isActive();
    boolean receiveReplicaWrite(Message message, IdempotencyKey key, IdempotencyRecord record);
    void commitMessage(String messageId);
    List<Message> getLatestMessages(String conversationId, int limit);
    int getMaxVersion(String conversationId);
}
