//Micah Chen & Archie Phyo
package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Operation;
import java.util.concurrent.ThreadLocalRandom;

public final class Producer implements Runnable {
    private final int producerNumber;
    private final OperationRepository repository;
    private final CoordinatorDashboard dashboard;
    private final CoordinatorStats stats;
    private final long delayMs;

    public Producer(int producerNumber, OperationRepository repository, CoordinatorDashboard dashboard,
                    CoordinatorStats stats, long delayMs) {
        this.producerNumber = producerNumber; this.repository = repository;
        this.dashboard = dashboard; this.stats = stats; this.delayMs = delayMs;
    }

    @Override public void run() {
        dashboard.log("Producer " + producerNumber + " started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Operation operation = randomOperation();
                repository.put(operation);
                stats.generated.incrementAndGet();
                dashboard.operationGenerated(producerNumber, operation, repository.size(), stats);
                Thread.sleep(delayMs);
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        finally { dashboard.log("Producer " + producerNumber + " stopped"); }
    }

    private Operation randomOperation() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Operation.Operator operator = Operation.Operator.values()[r.nextInt(Operation.Operator.values().length)];
        int left = r.nextInt(1, 51);
        int right = r.nextInt(1, 21);
        if (operator == Operation.Operator.DIVIDE) left = right * r.nextInt(1, 11); // clean demo results
        return new Operation(left, right, operator);
    }
}
