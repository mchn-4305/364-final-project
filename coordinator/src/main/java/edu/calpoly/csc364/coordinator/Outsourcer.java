package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Json;
import edu.calpoly.csc364.common.Messages;
import edu.calpoly.csc364.common.Operation;
import edu.calpoly.csc364.common.Topics;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

public final class Outsourcer implements AutoCloseable {
    private final OperationRepository repository;
    private final CoordinatorDashboard dashboard;
    private final CoordinatorStats stats;
    private final long timeoutMs;
    private final MqttAsyncClient client;
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2);
    private final ExecutorService messageHandlers = Executors.newCachedThreadPool();
    private final Map<String, PendingJob> pendingByJob = new ConcurrentHashMap<>(); // NoteTaker
    private final Map<String, String> jobByWorker = new ConcurrentHashMap<>();

    public Outsourcer(String broker, OperationRepository repository, CoordinatorDashboard dashboard,
                      CoordinatorStats stats, long timeoutMs) throws MqttException {
        this.repository = repository; this.dashboard = dashboard; this.stats = stats; this.timeoutMs = timeoutMs;
        this.client = new MqttAsyncClient(broker, "coordinator-" + MqttAsyncClient.generateClientId(), new MemoryPersistence());
    }

    public void start() throws MqttException {
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String serverURI) {
                dashboard.log("MQTT connected to " + serverURI + (reconnect ? " (reconnected)" : ""));
                try { subscribeAll(); } catch (MqttException e) { dashboard.log("Subscription error: " + e.getMessage()); }
            }
            @Override public void connectionLost(Throwable cause) { dashboard.log("MQTT connection lost: " + cause.getMessage()); }
            @Override public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                messageHandlers.submit(() -> handleMessage(topic, payload));
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true); options.setCleanSession(true); options.setConnectionTimeout(10);
        client.connect(options).waitForCompletion();
        subscribeAll();
    }

    private void subscribeAll() throws MqttException {
        if (!client.isConnected()) return;
        client.subscribe(Topics.AVAILABLE, 1);
        client.subscribe(Topics.RESULTS, 1);
        client.subscribe(Topics.STATUS, 1);
    }

    private void handleMessage(String topic, String payload) {
        try {
            if (Topics.AVAILABLE.equals(topic)) handleAvailability(Json.decode(payload, Messages.Availability.class));
            else if (Topics.RESULTS.equals(topic)) handleResult(Json.decode(payload, Messages.Result.class));
            else if (Topics.STATUS.equals(topic)) handleStatus(Json.decode(payload, Messages.WorkerStatus.class));
        } catch (Exception e) { dashboard.log("Bad MQTT message on " + topic + ": " + e.getMessage()); }
    }

    private void handleAvailability(Messages.Availability request) throws Exception {
        if (request == null || request.workerId == null || request.workerId.isBlank()) return;
        dashboard.workerAvailable(request.workerId);
        if (jobByWorker.containsKey(request.workerId)) {
            dashboard.log("Deferred availability from busy worker " + request.workerId);
            timer.schedule(() -> retryWorker(request.workerId), timeoutMs + 100, TimeUnit.MILLISECONDS);
            return;
        }
        Operation operation = repository.tryTake(250, TimeUnit.MILLISECONDS);
        if (operation == null) {
            dashboard.workerIdle(request.workerId, "No pending operation yet");
            timer.schedule(() -> retryWorker(request.workerId), 500, TimeUnit.MILLISECONDS);
            return;
        }
        assign(request.workerId, operation);
    }

    private void retryWorker(String workerId) {
        if (!client.isConnected() || jobByWorker.containsKey(workerId)) return;
        try {
            Operation operation = repository.tryTake(100, TimeUnit.MILLISECONDS);
            if (operation == null) {
                timer.schedule(() -> retryWorker(workerId), 500, TimeUnit.MILLISECONDS);
            } else assign(workerId, operation);
        } catch (Exception e) { dashboard.log("Retry assignment failed for " + workerId + ": " + e.getMessage()); }
    }

    private void assign(String workerId, Operation operation) throws MqttException {
        PendingJob pending = new PendingJob(operation, workerId);
        pendingByJob.put(operation.getId(), pending);
        jobByWorker.put(workerId, operation.getId());
        pending.timeoutTask = timer.schedule(() -> recoverTimedOut(operation.getId()), timeoutMs, TimeUnit.MILLISECONDS);
        publish(Topics.assignment(workerId), Json.encode(new Messages.Assignment(workerId, operation)), 1);
        stats.outsourced.incrementAndGet();
        dashboard.outsourced(workerId, operation, repository.size(), stats);
    }

    private void handleResult(Messages.Result result) {
        if (result == null || result.jobId == null) return;
        PendingJob pending = pendingByJob.remove(result.jobId);
        if (pending == null) {
            dashboard.log("Ignored late/duplicate result for job " + shortId(result.jobId));
            return;
        }
        jobByWorker.remove(pending.workerId, result.jobId);
        if (pending.timeoutTask != null) pending.timeoutTask.cancel(false);
        if (result.error == null) {
            stats.remoteCompleted.incrementAndGet();
            dashboard.remoteCompleted(result.workerId, pending.operation, result.value, repository.size(), stats);
        } else {
            dashboard.log("Remote worker " + result.workerId + " failed " + pending.operation.expression() + ": " + result.error);
            recover(pending, "remote error");
        }
    }

    private void recoverTimedOut(String jobId) {
        PendingJob pending = pendingByJob.remove(jobId);
        if (pending == null) return;
        jobByWorker.remove(pending.workerId, jobId);
        recover(pending, "timeout after " + timeoutMs + " ms");
    }

    private void recover(PendingJob pending, String reason) {
        try {
            repository.put(pending.operation);
            stats.recovered.incrementAndGet();
            dashboard.recovered(pending.workerId, pending.operation, reason, repository.size(), stats);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void handleStatus(Messages.WorkerStatus status) {
        if (status == null || status.workerId == null) return;
        dashboard.workerStatus(status.workerId, status.status);
        if ("disconnected".equalsIgnoreCase(status.status)) {
            String jobId = jobByWorker.get(status.workerId);
            if (jobId != null) recoverTimedOut(jobId);
        }
    }

    private void publish(String topic, String payload, int qos) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos); message.setRetained(false); client.publish(topic, message);
    }

    private static String shortId(String id) { return id == null ? "?" : id.substring(0, Math.min(8, id.length())); }

    @Override public void close() {
        timer.shutdownNow(); messageHandlers.shutdownNow();
        try { if (client.isConnected()) client.disconnect().waitForCompletion(2000); client.close(); }
        catch (MqttException ignored) { }
    }
}
