# Distributed Messaging System

This project implements a fault-tolerant distributed messaging system. It includes components for **Consensus & Agreement** and **Time Synchronization**.

## Features

### Consensus & Agreement
- Demonstrates real-time message delivery across servers.
- Utilizes the **Raft Consensus Algorithm** to ensure consistent data storage, leader election, and high availability against node failures.

### Time Synchronization
- **NTP-like Physical Clock Synchronization**: Nodes periodically sync with a server to calculate clock offset and network delay.
- **Hybrid Logical Clocks (HLC)**: Combines physical time and logical counters to ensure causal ordering even when physical clocks drift.
- **Message Reordering Buffer**: Buffers incoming messages and processes them in the correct HLC order.

## Prerequisites
- Java 11 or higher
- Maven 3.6+

## How to Run

1. **Compilation**: Run the following command in the root folder to compile the project:
   ```bash
   mvn clean install
   ```

2. **Run the Server (gRPC)**:
   ```bash
   java -cp target/ds-messaging-system-0.0.1-SNAPSHOT.jar com.dsmessaging.MessagingServer 50051 server-1
   ```

3. **Run the Client (gRPC)**:
   ```bash
   java -cp target/ds-messaging-system-0.0.1-SNAPSHOT.jar com.dsmessaging.MessagingClient localhost 50051 client-1
   ```

4. **Run Raft Simulation (Consensus Component)**:
   To run the full Raft fault-tolerance and consensus simulation:
   ```bash
   mvn exec:java -Dexec.mainClass="com.dsmessaging.RaftSimulation"
   ```
   *Expected Output*: The console will output the nodes starting up, randomized leader election, simulated RPC message broadcast, and fail-over mechanisms.

## Project Structure
- `com.dsmessaging.sync.*`: Core HLC logic, ClockSync, MessageBuffer
- `com.dsmessaging.raft.*`: Raft consensus algorithm, Leader election
- `com.dsmessaging.MessagingServer` and `com.dsmessaging.MessagingClient`: gRPC endpoints
