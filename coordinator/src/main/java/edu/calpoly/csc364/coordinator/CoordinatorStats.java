package edu.calpoly.csc364.coordinator;

import java.util.concurrent.atomic.AtomicLong;

public final class CoordinatorStats {
    public final AtomicLong generated = new AtomicLong();
    public final AtomicLong localCompleted = new AtomicLong();
    public final AtomicLong outsourced = new AtomicLong();
    public final AtomicLong remoteCompleted = new AtomicLong();
    public final AtomicLong recovered = new AtomicLong();
}
