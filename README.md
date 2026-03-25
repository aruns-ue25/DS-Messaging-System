# Distributed Messaging System - Consensus & Agreement

## Team Information
- **Member 1 Name**: [Pending]
- **Registration**: [Pending]
- **Email**: [Pending]

*(Note: Other team members should fill out their respective parts above)*

## Project Overview
This project simulates a fault-tolerant distributed messaging system. It demonstrates real-time message delivery across servers, utilizing the **Raft Consensus Algorithm** to ensure consistent data storage, leader election, and high availability against node failures.

## Instructions for Running the Prototype
1. **Prerequisites**: Ensure you have Java 11 (or higher) and Maven installed correctly.
2. **Compilation**: Run the following command in the root folder to compile the project:
   ```bash
   mvn clean compile
   ```
3. **Execution**: To run the full Raft fault-tolerance and consensus simulation:
   ```bash
   mvn exec:java -Dexec.mainClass="com.dsmessaging.RaftSimulation"
   ```
4. **Expected Output**: The console will output the nodes starting up, randomized leader election, simulated RPC message broadcast, and fail-over mechanisms kicking in when the Leader node explicitly crashes.
