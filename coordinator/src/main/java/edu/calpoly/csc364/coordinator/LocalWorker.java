//Micah Chen & Archie Phyo
package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Operation;

public final class LocalWorker implements Runnable {
    private final int workerNumber;
    private final OperationRepository repository;
    private final CoordinatorDashboard dashboard;
    private final CoordinatorStats stats;
    private final long solveDelayMs;

    public LocalWorker(int workerNumber, OperationRepository repository, CoordinatorDashboard dashboard,
                       CoordinatorStats stats, long solveDelayMs) {
        this.workerNumber = workerNumber; this.repository = repository;
        this.dashboard = dashboard; this.stats = stats; this.solveDelayMs = solveDelayMs;
    }

    @Override public void run() {
        dashboard.log("Local worker " + workerNumber + " started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Operation operation = repository.take();
                dashboard.localStarted(workerNumber, operation, repository.size());
                Thread.sleep(solveDelayMs);
                String value = operation.solve().toPlainString();
                stats.localCompleted.incrementAndGet();
                dashboard.localCompleted(workerNumber, operation, value, repository.size(), stats);
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        finally { dashboard.log("Local worker " + workerNumber + " stopped"); }
    }
}
