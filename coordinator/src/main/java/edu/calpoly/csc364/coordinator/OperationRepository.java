package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Operation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class OperationRepository {
    private final Deque<Operation> queue = new ArrayDeque<>();
    private final Semaphore availableItems = new Semaphore(0, true);
    private final Semaphore availableSpaces;
    private final ReentrantLock lock = new ReentrantLock(true);

    public OperationRepository(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        availableSpaces = new Semaphore(capacity, true);
    }

    public void put(Operation operation) throws InterruptedException {
        availableSpaces.acquire();
        boolean inserted = false;
        try {
            lock.lockInterruptibly();
            try { queue.addLast(operation); inserted = true; }
            finally { lock.unlock(); }
        } finally {
            if (inserted) availableItems.release(); else availableSpaces.release();
        }
    }

    public Operation take() throws InterruptedException {
        availableItems.acquire();
        Operation operation = null;
        try {
            lock.lockInterruptibly();
            try { operation = queue.removeFirst(); }
            finally { lock.unlock(); }
            return operation;
        } finally {
            if (operation != null) availableSpaces.release(); else availableItems.release();
        }
    }

    public Operation tryTake(long timeout, TimeUnit unit) throws InterruptedException {
        if (!availableItems.tryAcquire(timeout, unit)) return null;
        Operation operation = null;
        try {
            lock.lockInterruptibly();
            try { operation = queue.removeFirst(); }
            finally { lock.unlock(); }
            return operation;
        } finally {
            if (operation != null) availableSpaces.release(); else availableItems.release();
        }
    }

    public int size() {
        lock.lock();
        try { return queue.size(); }
        finally { lock.unlock(); }
    }
}
