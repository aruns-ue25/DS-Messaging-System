# Distributed Messaging System

Team Members: 
Sugarthan Arun - IT24200310 
Jadavan M. - IT24103433 
Ilzam M.I.M. - IT24610792 
Heshanth T. - IT24101633

The Distributed Messaging System (DS-Messaging-System) is a high-availability, fault-tolerant messaging platform designed to ensure reliable communication across distributed environments. It tackles the core challenges of distributed systems—consistency, availability, and partial failure—by implementing industry-standard algorithms in a robust Java-based architecture.

At its core, the system uses the Raft Consensus Algorithm to maintain a consistent global state and coordinate leader election. To solve the problem of message ordering in a distributed network, it integrates Hybrid Logical Clocks (HLC) and an NTP-like time synchronization mechanism, ensuring that messages are processed in their correct causal order regardless of physical clock drift. With Quorum-based replication (W=2, R=1), the system guarantees that data survives node crashes, providing a seamless and reliable experience for distributed applications.

⚙️ Prerequisites
To build and run this project, ensure you have the following installed:

Java Development Kit (JDK): Version 11 or higher.
Apache Maven: Version 3.6 or higher (for dependency management and building).
MySQL Server: (Version 8.0+) Ensure you have a running MySQL instance. The system uses it for persistent storage of messages and idempotency records.
Modern Web Browser: (e.g., Chrome, Edge, or Firefox) to access the interactive dashboard.
Port Availability: Ensure ports 9090 (UI) and the internal gRPC ports (defined in the configuration) are not in use.
🚀 Steps to Run the Project
Follow these steps to get the 3-node cluster and dashboard up and running:

1. Database Setup
Execute the following SQL script located in the project to initialize the required tables in your MySQL database:

bash
# Locate and run the schema file against your MySQL instance
src/main/resources/schema.sql
2. Build the Project
Open a terminal in the project root directory and run the Maven install command. This will compile the source code and generate the necessary gRPC and Protobuf classes:

bash
mvn clean install
3. Launch the Cluster & Dashboard
You can start the entire 3-node simulated cluster and the interactive UI server simultaneously using the Main class:

bash
mvn exec:java -Dexec.mainClass="com.dsmessaging.Main"
Alternatively, you can run the JAR file from the target/ directory after building.

4. Access the Interactive UI
Once the console indicates that the cluster and UI server have started, open your web browser and navigate to:

text
http://localhost:9090
From here, you can simulate message sending, observe real-time replication across nodes, and monitor the Raft leader election process.

🛠️ Key Technical Features
Consensus & Agreement: Powered by Raft for leader election and log consistency.
Time Synchronization: Combines NTP-like Physical Clock Sync with Hybrid Logical Clocks (HLC) for causal ordering.
Fault Tolerance: Automatic recovery and data synchronization for nodes joining or returning to the cluster.
Interactive Dashboard: A real-time web interface to visualize system state and simulate various network scenarios.
