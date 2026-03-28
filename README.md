# Distributed Messaging System - Time Synchronization

This project implements a fault-tolerant distributed messaging system. This README focuses on the **Time Synchronization** component (Member 3).

## Features
- **NTP-like Physical Clock Synchronization**: Nodes periodically sync with a server to calculate clock offset and network delay.
- **Hybrid Logical Clocks (HLC)**: Combines physical time and logical counters to ensure causal ordering even when physical clocks drift.
- **Message Reordering Buffer**: Buffers incoming messages and processes them in the correct HLC order.

## Project Structure
- `com.dsmessaging.sync.HybridLogicalClock`: Core HLC logic.
- `com.dsmessaging.sync.ClockSync`: NTP-like offset calculation.
- `com.dsmessaging.sync.MessageBuffer`: Reordering logic.
- `com.dsmessaging.MessagingServer`: gRPC server endpoint.
- `com.dsmessaging.MessagingClient`: gRPC client with periodic sync.

## Prerequisites
- Java 11 or higher
- Maven 3.6+

## How to Run

1. **Compile the project**:
   ```bash
   mvn clean install
   ```

2. **Start the Server**:
   ```bash
   java -cp target/ds-messaging-system-0.0.1-SNAPSHOT.jar com.dsmessaging.MessagingServer 50051 server-1
   ```

3. **Start the Client**:
   ```bash
   java -cp target/ds-messaging-system-0.0.1-SNAPSHOT.jar com.dsmessaging.MessagingClient localhost 50051 client-1
   ```

## Design Details (Member 3 Objectives)

### 1. NTP-like Protocol
We use a standard offset + delay calculation. Every 5 seconds, the client sends a `SyncRequest` to the server. By measuring the round-trip time and comparing timestamps, we calculate the estimated offset.

### 2. Hybrid Logical Clocks
Physical clocks alone are not enough due to potential clock skew. HLC ensure that if `Event A` happens before `Event B`, its timestamp will always be smaller, even if the nodes' physical clocks are slightly out of sync.

### 3. Reordering Mechanism
Messages are added to a `PriorityQueue` based on their HLC timestamps. We wait for a short period (500ms) before processing them to ensure that any messages delayed by the network have a chance to arrive and be inserted in the correct order.
