package com.dsmessaging.service;

import com.dsmessaging.model.IdempotencyKey;
import com.dsmessaging.model.IdempotencyRecord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyStore {
    private final Map<IdempotencyKey, IdempotencyRecord> store = new ConcurrentHashMap<>();

    public IdempotencyRecord getRecord(IdempotencyKey key) {
        return store.get(key);
    }

    public void putRecord(IdempotencyKey key, IdempotencyRecord record) {
        store.put(key, record);
    }
    
    public int getSize() {
        return store.size();
    }
}
