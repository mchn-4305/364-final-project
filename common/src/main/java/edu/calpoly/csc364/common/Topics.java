//Micah Chen & Archie Phyo
package edu.calpoly.csc364.common;

public final class Topics {
    public static final String BASE = "csc364/distributed-pc";
    public static final String AVAILABLE = BASE + "/workers/available";
    public static final String STATUS = BASE + "/workers/status";
    public static final String RESULTS = BASE + "/jobs/results";
    public static final String ASSIGNMENT_PREFIX = BASE + "/jobs/assign/";
    private Topics() { }
    public static String assignment(String workerId) { return ASSIGNMENT_PREFIX + workerId; }
}
