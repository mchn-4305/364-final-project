package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Operation;
import java.util.concurrent.ScheduledFuture;

final class PendingJob {
    final Operation operation;
    final String workerId;
    final long assignedAt;
    volatile ScheduledFuture<?> timeoutTask;
    PendingJob(Operation operation, String workerId) {
        this.operation = operation; this.workerId = workerId; this.assignedAt = System.currentTimeMillis();
    }
}
