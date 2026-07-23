//Micah Chen & Archie Phyo
package edu.calpoly.csc364.coordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoordinatorMain {
    private CoordinatorMain() { }
    public static void main(String[] args) throws Exception {
        Config c = Config.from(args);
        OperationRepository repository = new OperationRepository(c.capacity);
        CoordinatorStats stats = new CoordinatorStats();
        CoordinatorDashboard dashboard = new CoordinatorDashboard(c.producers, c.localWorkers);
        ExecutorService pool = Executors.newFixedThreadPool(c.producers + c.localWorkers);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 1; i <= c.producers; i++) tasks.add(new Producer(i, repository, dashboard, stats, c.producerDelayMs));
        for (int i = 1; i <= c.localWorkers; i++) tasks.add(new LocalWorker(i, repository, dashboard, stats, c.localDelayMs));
        tasks.forEach(pool::submit);
        Outsourcer outsourcer = new Outsourcer(c.broker, repository, dashboard, stats, c.timeoutMs);
        outsourcer.start();
        dashboard.log("Coordinator ready. Broker=" + c.broker + ", timeout=" + c.timeoutMs + " ms");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { outsourcer.close(); pool.shutdownNow(); }, "coordinator-shutdown"));
    }

    private record Config(String broker, int producers, int localWorkers, int capacity,
                          long producerDelayMs, long localDelayMs, long timeoutMs) {
        static Config from(String[] args) {
            String broker = value(args, "--broker", env("MQTT_BROKER", "tcp://localhost:1883"));
            return new Config(broker,
                    integer(args, "--producers", 2), integer(args, "--local-workers", 2), integer(args, "--capacity", 40),
                    number(args, "--producer-delay", 650), number(args, "--local-delay", 1100), number(args, "--timeout", 5000));
        }
        static String value(String[] a, String key, String d) { for (int i=0;i<a.length-1;i++) if (a[i].equals(key)) return a[i+1]; return d; }
        static int integer(String[] a, String k, int d) { return Integer.parseInt(value(a,k,String.valueOf(d))); }
        static long number(String[] a, String k, long d) { return Long.parseLong(value(a,k,String.valueOf(d))); }
        static String env(String k, String d) { String v=System.getenv(k); return v==null||v.isBlank()?d:v; }
    }
}
