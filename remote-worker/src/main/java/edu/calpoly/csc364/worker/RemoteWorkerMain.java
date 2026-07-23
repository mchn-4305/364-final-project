//Micah Chen & Archie Phyo
package edu.calpoly.csc364.worker;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class RemoteWorkerMain {
    private RemoteWorkerMain() { }
    public static void main(String[] args) throws Exception {
        String broker = value(args, "--broker", env("MQTT_BROKER", "tcp://localhost:1883"));
        String defaultId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 4);
        String id = value(args, "--id", defaultId).replaceAll("[^A-Za-z0-9_-]", "_");
        long delay = Long.parseLong(value(args, "--delay", "1200"));
        boolean dropFirst = has(args, "--drop-first-result");
        RemoteWorkerNode node = new RemoteWorkerNode(broker, id, delay, dropFirst);
        node.start();
        Runtime.getRuntime().addShutdownHook(new Thread(node::close, "worker-shutdown"));
        System.out.println("Remote worker " + id + " ready. Press Ctrl+C to stop.");
        new CountDownLatch(1).await();
    }
    private static String value(String[] a, String key, String d) { for (int i=0;i<a.length-1;i++) if (a[i].equals(key)) return a[i+1]; return d; }
    private static boolean has(String[] a, String key) { for (String s:a) if (s.equals(key)) return true; return false; }
    private static String env(String k, String d) { String v=System.getenv(k); return v==null||v.isBlank()?d:v; }
}
