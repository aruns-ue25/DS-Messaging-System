package com.dsmessaging;

import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MessagingServer {
    private static final Logger logger = LoggerFactory.getLogger(MessagingServer.class);
    
    private final int port;
    private final Server server;

    public MessagingServer(int port, String nodeId) {
        this.port = port;
        HybridLogicalClock hlc = new HybridLogicalClock(nodeId);
        ClockSync clockSync = new ClockSync();
        
        this.server = ServerBuilder.forPort(port)
                .addService(new MessagingServiceImpl(hlc, clockSync))
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            MessagingServer.this.stop();
            logger.info("Server shut down");
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 50051;
        String nodeId = (args.length > 1) ? args[1] : "node-1";
        
        MessagingServer server = new MessagingServer(port, nodeId);
        server.start();
        server.blockUntilShutdown();
    }
}
