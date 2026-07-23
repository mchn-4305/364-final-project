# CSC 364 Final Project

This repository contains two independent runnable applications:

- **Coordinator**: producers, bounded synchronized repository, local consumers, MQTT outsourcer, timeout recovery, and Swing dashboard.
- **Remote Worker**: pull-based MQTT worker that announces availability, receives one job, solves it, returns a result, and requests another job.

A small `common` Maven module holds the shared operation model and MQTT protocol classes used by both applications.

## Requirements

- Java 17+
- Maven 3.8+
- An MQTT 3.1.1 broker, such as Mosquitto, listening on port 1883

## Build

```bash
mvn clean package
```

Runnable JARs are produced at:

```text
coordinator/target/coordinator.jar
remote-worker/target/remote-worker.jar
```

## Run

Start the MQTT broker first. With Mosquitto installed:

```bash
mosquitto -v
```

Start the coordinator:

```bash
java -jar coordinator/target/coordinator.jar
```

Start one or more workers in separate terminals:

```bash
java -jar remote-worker/target/remote-worker.jar --id worker-1
java -jar remote-worker/target/remote-worker.jar --id worker-2
```

To demonstrate timeout recovery, make a worker intentionally discard its first result:

```bash
java -jar remote-worker/target/remote-worker.jar --id timeout-demo --drop-first-result
```

The coordinator timeout defaults to 5000 ms. It can be shortened for a video demonstration:

```bash
java -jar coordinator/target/coordinator.jar --timeout 3000
```

## Useful arguments

Coordinator:

```text
--broker tcp://localhost:1883
--producers 2
--local-workers 2
--capacity 40
--producer-delay 650
--local-delay 1100
--timeout 5000
```

Remote worker:

```text
--broker tcp://localhost:1883
--id worker-1
--delay 1200
--drop-first-result
```

`MQTT_BROKER` may also be set as an environment variable.

## MQTT protocol

| Purpose | Topic | Publisher | JSON payload |
|---|---|---|---|
| Worker asks for work | `csc364/distributed-pc/workers/available` | Remote worker | `workerId`, `timestamp` |
| Worker lifecycle | `csc364/distributed-pc/workers/status` | Remote worker | `workerId`, `status`, `timestamp` |
| Job assignment | `csc364/distributed-pc/jobs/assign/{workerId}` | Coordinator | `workerId`, `operation`, `assignedAt` |
| Job result | `csc364/distributed-pc/jobs/results` | Remote worker | `workerId`, `jobId`, `expression`, `value/error`, `finishedAt` |

All messages use JSON and QoS 1. Assignment topics are worker-specific, so each worker only receives its own work.

## Pull-model sequence

1. A remote worker connects and subscribes to its assignment topic.
2. It publishes an availability request.
3. The coordinator removes one operation from the shared repository.
4. The coordinator records the operation in its pending-job map and starts a timeout task.
5. The coordinator publishes the assignment to that worker's topic.
6. The worker solves and publishes a result.
7. The coordinator removes the pending record and cancels its timeout.
8. The worker publishes availability again.

If the timeout fires first, the coordinator removes the pending record and puts the operation back into the shared repository. A late result is ignored, preventing the same job from being counted twice.

## Video sequence

1. Show the broker running.
2. Start the coordinator and identify producers, repository, local consumers, and dashboard.
3. Start `worker-1`; point out that it announces availability before receiving work.
4. Start `worker-2`; show two dynamic worker panels and concurrent completions.
5. Start `timeout-demo --drop-first-result`; show the recovered-job event.
6. Briefly explain the four MQTT topics and JSON payloads.
7. Explain team contributions.