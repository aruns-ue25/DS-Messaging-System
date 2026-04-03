package com.dsmessaging.ui;

import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded HTTP server to expose the Messaging System via REST API and a web dashboard.
 * Uses only built-in Java libraries.
 */
public class UIServer {
    private static final Logger logger = Logger.getLogger(UIServer.class.getName());
    private final int port;
    private final MessagingSystem cluster;
    private HttpServer server;

    public UIServer(int port, MessagingSystem cluster) {
        this.port = port;
        this.cluster = cluster;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // --- API Endpoints ---
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/cluster", new ClusterHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/messages", new MessageListHandler());
        server.createContext("/api/write", new WriteHandler());
        server.createContext("/api/fail/", new NodeActionHandler("fail"));
        server.createContext("/api/recover/", new NodeActionHandler("recover"));
        server.createContext("/api/hlc", new HlcHandler());
        
        // --- Static UI File (index.html) ---
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default executor
        server.start();
        System.out.println("\nDashboard started on http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- Handlers ---

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, "{\"status\":\"UP\"}", "application/json", 200);
        }
    }

    private class ClusterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                StringBuilder json = new StringBuilder("[");
                List<ServerNode> nodes = cluster.getServers();
                for (int i = 0; i < nodes.size(); i++) {
                    ServerNode node = nodes.get(i);
                    json.append("{")
                        .append("\"id\":\"").append(node.getServerId()).append("\",")
                        .append("\"active\":").append(node.isActive()).append(",")
                        .append("\"role\":\"").append(node.getRaftNode().getState().toString()).append("\",")
                        .append("\"term\":").append(node.getRaftNode().getCurrentTerm()).append(",")
                        .append("\"msgCount\":").append(node.getMessageStore().getLatestMessages("default-conv", 100).size())
                        .append("}");
                    if (i < nodes.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(exchange, json.toString(), "application/json", 200);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error building cluster JSON", e);
                sendResponse(exchange, "{\"error\":\"" + e.getMessage() + "\"}", "application/json", 500);
            }
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var m = cluster.getGlobalMetrics();
            String json = "{" +
                "\"writes\":" + m.successfulQuorumWrites.get() + "," +
                "\"reads\":" + m.successfulQuorumReads.get() + "," +
                "\"dupes\":" + m.duplicatesSuppressed.get() + "," +
                "\"recoveries\":" + m.recoveryEvents.get() +
                "}";
            sendResponse(exchange, json, "application/json", 200);
        }
    }

    private class MessageListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String q = exchange.getRequestURI().getQuery();
            String convId = "default-conv"; 
            String nodeId = null; 
            
            if (q != null) {
                if (q.contains("convId=")) convId = q.split("convId=")[1].split("&")[0];
                if (q.contains("nodeId=")) nodeId = q.split("nodeId=")[1].split("&")[0];
            }

            try {
                List<com.dsmessaging.model.Message> results;
                
                // If a specific nodeId is requested, we do a LOCAL read from that node's store
                if (nodeId != null && !nodeId.isEmpty() && !nodeId.equals("undefined")) {
                    ServerNode target = cluster.getServer(nodeId);
                    if (target != null) {
                        results = target.getMessageStore().getLatestMessages(convId, 20);
                    } else {
                        results = List.of();
                    }
                } else {
                    // Fallback: Perform a Quorum Read (R=2) from any active node
                    ServerNode activeNode = null;
                    for (ServerNode sn : cluster.getServers()) if (sn.isActive()) { activeNode = sn; break; }
                    
                    if (activeNode == null) {
                        sendResponse(exchange, "[]", "application/json", 200);
                        return;
                    }
                    results = activeNode.handleClientReadLatest(convId, 20, new com.dsmessaging.model.ClientSession("ui", "UI"));
                }

                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    var m = results.get(i);
                    json.append("{")
                        .append("\"messageId\":\"").append(m.getMessageId()).append("\",")
                        .append("\"senderId\":\"").append(m.getSenderId()).append("\",")
                        .append("\"content\":\"").append(m.getContent().replace("\"", "\\\"")).append("\",")
                        .append("\"commitVersion\":").append(m.getCommitVersion()).append(",")
                        .append("\"timestamp\":").append(m.getCreatedAt())
                        .append("}");
                    if (i < results.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(exchange, json.toString(), "application/json", 200);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error fetching messages", e);
                sendResponse(exchange, "[]", "application/json", 200);
            }
        }
    }

    private class WriteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, "Method not allowed", "text/plain", 405);
                return;
            }
            
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String sender = extractJsonValue(body, "sender");
                String receiver = extractJsonValue(body, "receiver");
                String content = extractJsonValue(body, "content");
                String convId = extractJsonValue(body, "convId");
                String reqId = extractJsonValue(body, "reqId");

                var request = new com.dsmessaging.model.ClientWriteRequest(convId, sender, receiver, content, reqId);
                var session = new com.dsmessaging.model.ClientSession("ui-" + sender, sender);
                
                ServerNode coordinator = null;
                for (ServerNode sn : cluster.getServers()) if (sn.isActive()) { coordinator = sn; break; }
                
                if (coordinator == null) {
                    sendResponse(exchange, "{\"error\":\"No active nodes available\"}", "application/json", 503);
                    return;
                }

                var result = coordinator.handleClientWrite(request, session);
                sendResponse(exchange, "{\"status\":\"OK\",\"version\":" + result.getCommitVersion() + "}", "application/json", 200);
            } catch (Exception e) {
                sendResponse(exchange, "{\"error\":\"" + e.getMessage() + "\"}", "application/json", 400);
            }
        }

        private String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\":\"([^\"]*)\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            return m.find() ? m.group(1) : "";
        }
    }

    private class NodeActionHandler implements HttpHandler {
        private final String action;
        public NodeActionHandler(String action) { this.action = action; }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            
            try {
                ServerNode node = cluster.getServer(id);
                if (action.equals("fail")) {
                    node.failNode();
                    System.out.println("UI Action: Failed " + id);
                } else {
                    node.recoverNode();
                    System.out.println("UI Action: Recovered " + id);
                    
                    // Bi-directional Cluster-wide Sync
                    List<ServerNode> allNodes = cluster.getServers();
                    for (ServerNode peer : allNodes) {
                        if (peer.isActive()) {
                            // 1. Recovered node pulls from all other active peers
                            if (!peer.getServerId().equals(id)) {
                                node.recoverFrom(peer, "default-conv");
                                // 2. All other active peers also pull from the newly recovered node
                                peer.recoverFrom(node, "default-conv");
                            }
                        }
                    }
                }
                sendResponse(exchange, "{\"status\":\"OK\"}", "application/json", 200);
            } catch (Exception e) {
                sendResponse(exchange, "{\"error\":\"" + e.getMessage() + "\"}", "application/json", 404);
            }
        }
    }

    private class HlcHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                StringBuilder json = new StringBuilder("[");
                List<ServerNode> nodes = cluster.getServers();
                for (int i = 0; i < nodes.size(); i++) {
                    ServerNode node = nodes.get(i);
                    var hlc = node.getHlc();
                    json.append("{")
                        .append("\"id\":\"").append(node.getServerId()).append("\",")
                        .append("\"hlc\":\"").append(hlc.toString()).append("\"")
                        .append("}");
                    if (i < nodes.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(exchange, json.toString(), "application/json", 200);
            } catch (Exception e) {
                sendResponse(exchange, "[]", "application/json", 200);
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            InputStream is = getClass().getResourceAsStream("/ui" + path);
            if (is == null) {
                sendResponse(exchange, "404 Not Found", "text/plain", 404);
                return;
            }

            byte[] content = is.readAllBytes();
            String contentType = path.endsWith(".html") ? "text/html" : "text/plain";
            sendResponse(exchange, new String(content), contentType, 200);
        }
    }

    // --- Helper ---
    private static void sendResponse(HttpExchange exchange, String response, String contentType, int code) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // For development
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
