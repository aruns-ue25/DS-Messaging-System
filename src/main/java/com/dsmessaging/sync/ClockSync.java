package com.dsmessaging.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles physical clock synchronization using an NTP-like algorithm.
 */
public class ClockSync {
    private static final Logger logger = LoggerFactory.getLogger(ClockSync.class);
    
    private long currentOffset = 0;
    private long lastSyncTime = 0;

    /**
     * Calculates and updates the clock offset.
     * @param t0 Client send time
     * @param t1 Server receive time
     * @param t2 Server transmit time
     * @param t3 Client receive time
     */
    public synchronized void updateOffset(long t0, long t1, long t2, long t3) {
        long delay = ((t3 - t0) - (t2 - t1)) / 2;
        long offset = ((t1 - t0) + (t2 - t3)) / 2;
        
        this.currentOffset = offset;
        this.lastSyncTime = System.currentTimeMillis();
        
        logger.info("Clock Sync: Delay={}ms, Offset={}ms. New Adjusted Time={}", 
                delay, offset, System.currentTimeMillis() + offset);
    }

    public synchronized long getAdjustedTime() {
        return System.currentTimeMillis() + currentOffset;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }
}
