package edu.calpoly.csc364.common;

public final class Messages {
    private Messages() { }

    public static final class Availability {
        public String workerId;
        public long timestamp;
        public Availability() { }
        public Availability(String workerId) { this.workerId = workerId; this.timestamp = System.currentTimeMillis(); }
    }

    public static final class WorkerStatus {
        public String workerId;
        public String status;
        public long timestamp;
        public WorkerStatus() { }
        public WorkerStatus(String workerId, String status) {
            this.workerId = workerId; this.status = status; this.timestamp = System.currentTimeMillis();
        }
    }

    public static final class Assignment {
        public String workerId;
        public Operation operation;
        public long assignedAt;
        public Assignment() { }
        public Assignment(String workerId, Operation operation) {
            this.workerId = workerId; this.operation = operation; this.assignedAt = System.currentTimeMillis();
        }
    }

    public static final class Result {
        public String workerId;
        public String jobId;
        public String expression;
        public String value;
        public String error;
        public long finishedAt;
        public Result() { }
        public static Result success(String workerId, Operation operation, String value) {
            Result r = new Result(); r.workerId = workerId; r.jobId = operation.getId();
            r.expression = operation.expression(); r.value = value; r.finishedAt = System.currentTimeMillis(); return r;
        }
        public static Result failure(String workerId, Operation operation, String error) {
            Result r = new Result(); r.workerId = workerId; r.jobId = operation.getId();
            r.expression = operation.expression(); r.error = error; r.finishedAt = System.currentTimeMillis(); return r;
        }
    }
}
