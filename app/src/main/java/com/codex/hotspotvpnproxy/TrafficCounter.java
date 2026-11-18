package com.codex.hotspotvpnproxy;

import java.util.concurrent.atomic.AtomicLong;

public final class TrafficCounter {
    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();

    public void addSent(long count) {
        if (count > 0) {
            sentBytes.addAndGet(count);
        }
    }

    public void addReceived(long count) {
        if (count > 0) {
            receivedBytes.addAndGet(count);
        }
    }

    public long getSentBytes() {
        return sentBytes.get();
    }

    public long getReceivedBytes() {
        return receivedBytes.get();
    }

    public void reset() {
        sentBytes.set(0);
        receivedBytes.set(0);
    }
}
