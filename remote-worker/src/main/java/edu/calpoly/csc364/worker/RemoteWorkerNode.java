//Micah Chen & Archie Phyo
package edu.calpoly.csc364.worker;

import edu.calpoly.csc364.common.Json;
import edu.calpoly.csc364.common.Messages;
import edu.calpoly.csc364.common.Operation;
import edu.calpoly.csc364.common.Topics;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteWorkerNode implements AutoCloseable {
    private final String workerId;
    private final long solveDelayMs;
    private final boolean dropFirstResult;
    private final Solver solver = new Solver();
    private final ExecutorService solverThread;
    private final MqttAsyncClient client;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean firstResultDropped = new AtomicBoolean(false);

    public RemoteWorkerNode(String broker, String workerId, long solveDelayMs, boolean dropFirstResult) throws MqttException {
        this.workerId = workerId; this.solveDelayMs = solveDelayMs; this.dropFirstResult = dropFirstResult;
        this.solverThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "solver-" + workerId));
        this.client = new MqttAsyncClient(broker, "remote-" + workerId, new MemoryPersistence());
    }

    public void start() throws MqttException {
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("[" + workerId + "] connected to " + serverURI);
                try {
                    client.subscribe(Topics.assignment(workerId), 1);
                    publish(Topics.STATUS, Json.encode(new Messages.WorkerStatus(workerId, "connected")), 1);
                    announceAvailable();
                } catch (MqttException e) { System.err.println("Connection setup failed: " + e.getMessage()); }
            }
            @Override public void connectionLost(Throwable cause) { System.err.println("[" + workerId + "] connection lost: " + cause.getMessage()); }
            @Override public void messageArrived(String topic, MqttMessage message) {
                String json = new String(message.getPayload(), StandardCharsets.UTF_8);
                Messages.Assignment assignment = Json.decode(json, Messages.Assignment.class);
                if (assignment == null || assignment.operation == null || !workerId.equals(assignment.workerId)) return;
                if (!busy.compareAndSet(false, true)) { System.err.println("Ignored assignment while busy"); return; }
                solverThread.submit(() -> solveAndReturn(assignment.operation));
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true); options.setCleanSession(true); options.setConnectionTimeout(10);
        options.setWill(Topics.STATUS, Json.encode(new Messages.WorkerStatus(workerId, "disconnected")).getBytes(StandardCharsets.UTF_8), 1, false);
        client.connect(options).waitForCompletion();
    }

    private void solveAndReturn(Operation operation) {
        try {
            System.out.println("[" + workerId + "] solving " + operation.expression());
            Thread.sleep(solveDelayMs);
            Messages.Result result;
            try { result = Messages.Result.success(workerId, operation, solver.solve(operation)); }
            catch (Exception e) { result = Messages.Result.failure(workerId, operation, e.getMessage()); }
            if (dropFirstResult && firstResultDropped.compareAndSet(false, true)) {
                System.out.println("[" + workerId + "] TEST MODE: dropped first result for timeout demonstration");
            } else {
                publish(Topics.RESULTS, Json.encode(result), 1);
                System.out.println("[" + workerId + "] returned " + operation.expression() + (result.error == null ? " = " + result.value : " error=" + result.error));
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (MqttException e) { System.err.println("Publish failed: " + e.getMessage()); }
        finally {
            busy.set(false);
            try { announceAvailable(); } catch (MqttException e) { System.err.println("Availability publish failed: " + e.getMessage()); }
        }
    }

    private void announceAvailable() throws MqttException {
        publish(Topics.AVAILABLE, Json.encode(new Messages.Availability(workerId)), 1);
        System.out.println("[" + workerId + "] available");
    }

    private void publish(String topic, String payload, int qos) throws MqttException {
        if (!client.isConnected()) throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos); message.setRetained(false); client.publish(topic, message);
    }

    @Override public void close() {
        solverThread.shutdownNow();
        try {
            if (client.isConnected()) {
                publish(Topics.STATUS, Json.encode(new Messages.WorkerStatus(workerId, "disconnected")), 1);
                client.disconnect().waitForCompletion(2000);
            }
            client.close();
        } catch (MqttException ignored) { }
    }
}
