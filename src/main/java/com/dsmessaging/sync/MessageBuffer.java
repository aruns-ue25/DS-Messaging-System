package com.dsmessaging.sync;

import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * Buffers incoming messages/tasks and reorders them based on HLC timestamps.
 */
public class MessageBuffer<T> {
    private final PriorityQueue<BufferedItem<T>> queue;
    private final long waitPeriodMs;
    private final Consumer<T> processor;

    public MessageBuffer(long waitPeriodMs, Consumer<T> processor) {
        this.queue = new PriorityQueue<>();
        this.waitPeriodMs = waitPeriodMs;
        this.processor = processor;
        
        // Background thread to release items
        Thread releaser = new Thread(this::releaseLoop);
        releaser.setDaemon(true);
        releaser.setName("MessageBufferReleaser");
        releaser.start();
    }

    public synchronized void addItem(T item, HybridLogicalClock hlc) {
        System.out.println("Message arrived out of order (potentially)");
        System.out.println("Placed in reordering buffer (HLC: " + hlc.toString() + ")");
        queue.add(new BufferedItem<>(item, hlc, System.currentTimeMillis()));
    }

    private void releaseLoop() {
        while (true) {
            try {
                Thread.sleep(50);
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
            BufferedItem<T> item = queue.poll();
            System.out.println("Reordered using logical timestamp: Released " + item.hlc);
            processor.accept(item.item);
        }
    }

    private static class BufferedItem<T> implements Comparable<BufferedItem<T>> {
        final T item;
        final HybridLogicalClock hlc;
        final long arrivalLocalTime;

        BufferedItem(T item, HybridLogicalClock hlc, long arrivalLocalTime) {
            this.item = item;
            this.hlc = hlc;
            this.arrivalLocalTime = arrivalLocalTime;
        }

        @Override
        public int compareTo(BufferedItem<T> other) {
            return this.hlc.compareTo(other.hlc);
        }
    }
}
