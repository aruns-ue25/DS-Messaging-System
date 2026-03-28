package com.dsmessaging.sync;

import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Buffers incoming messages and reorders them based on HLC timestamps.
 */
public class MessageBuffer {
    private final PriorityQueue<BufferedMessage> queue;
    private final long waitPeriodMs;
    private final Consumer<String> messageProcessor;

    public MessageBuffer(long waitPeriodMs, Consumer<String> messageProcessor) {
        this.queue = new PriorityQueue<>();
        this.waitPeriodMs = waitPeriodMs;
        this.messageProcessor = messageProcessor;
        
        // Background thread to release messages
        Thread releaser = new Thread(this::releaseLoop);
        releaser.setDaemon(true);
        releaser.start();
    }

    public synchronized void addMessage(String content, HybridLogicalClock hlc) {
        queue.add(new BufferedMessage(content, hlc, System.currentTimeMillis()));
    }

    private void releaseLoop() {
        while (true) {
            try {
                Thread.sleep(100);
                checkAndRelease();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private synchronized void checkAndRelease() {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && (now - queue.peek().arrivalLocalTime >= waitPeriodMs)) {
            BufferedMessage msg = queue.poll();
            messageProcessor.accept(msg.content);
        }
    }

    private static class BufferedMessage implements Comparable<BufferedMessage> {
        final String content;
        final HybridLogicalClock hlc;
        final long arrivalLocalTime;

        BufferedMessage(String content, HybridLogicalClock hlc, long arrivalLocalTime) {
            this.content = content;
            this.hlc = hlc;
            this.arrivalLocalTime = arrivalLocalTime;
        }

        @Override
        public int compareTo(BufferedMessage other) {
            return this.hlc.compareTo(other.hlc);
        }
    }
}
