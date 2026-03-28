package com.dsmessaging.sync;

import java.util.Objects;

/**
 * Implementation of Hybrid Logical Clock (HLC).
 * Provides causal ordering with a best-effort approximation of physical time.
 */
public class HybridLogicalClock implements Comparable<HybridLogicalClock> {
    private long wallTime; // l
    private int logicalCounter; // c
    private final String nodeId;

    public HybridLogicalClock(String nodeId) {
        this.nodeId = nodeId;
        this.wallTime = 0;
        this.logicalCounter = 0;
    }

    public HybridLogicalClock(long wallTime, int logicalCounter, String nodeId) {
        this.wallTime = wallTime;
        this.logicalCounter = logicalCounter;
        this.nodeId = nodeId;
    }

    public synchronized HybridLogicalClock updateLocal() {
        long now = System.currentTimeMillis();
        long oldWallTime = wallTime;

        wallTime = Math.max(oldWallTime, now);
        if (wallTime == oldWallTime) {
            logicalCounter++;
        } else {
            logicalCounter = 0;
        }
        return new HybridLogicalClock(wallTime, logicalCounter, nodeId);
    }

    public synchronized HybridLogicalClock updateRemote(long msgWallTime, int msgLogicalCounter) {
        long now = System.currentTimeMillis();
        long oldWallTime = wallTime;

        wallTime = Math.max(Math.max(oldWallTime, now), msgWallTime);

        if (wallTime == oldWallTime && wallTime == msgWallTime) {
            logicalCounter = Math.max(logicalCounter, msgLogicalCounter) + 1;
        } else if (wallTime == oldWallTime) {
            logicalCounter++;
        } else if (wallTime == msgWallTime) {
            logicalCounter = msgLogicalCounter + 1;
        } else {
            logicalCounter = 0;
        }
        return new HybridLogicalClock(wallTime, logicalCounter, nodeId);
    }

    public long getWallTime() {
        return wallTime;
    }

    public int getLogicalCounter() {
        return logicalCounter;
    }

    public String getNodeId() {
        return nodeId;
    }

    @Override
    public int compareTo(HybridLogicalClock other) {
        if (this.wallTime != other.wallTime) {
            return Long.compare(this.wallTime, other.wallTime);
        }
        if (this.logicalCounter != other.logicalCounter) {
            return Integer.compare(this.logicalCounter, other.logicalCounter);
        }
        return this.nodeId.compareTo(other.nodeId);
    }

    @Override
    public String toString() {
        return String.format("%d:%d:%s", wallTime, logicalCounter, nodeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HybridLogicalClock that = (HybridLogicalClock) o;
        return wallTime == that.wallTime &&
                logicalCounter == that.logicalCounter &&
                Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wallTime, logicalCounter, nodeId);
    }
}
