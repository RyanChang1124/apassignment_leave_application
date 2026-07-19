package org.mcp;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class McpClientService {
    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient httpClient;

    private static volatile String verifiedMessageEndpoint = null;
    private static final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    public McpClientService() {
        ExecutorService multiThreadExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-worker-daemon");
            t.setDaemon(true);
            return t;
        });

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(multiThreadExecutor)
                .build();

        if (verifiedMessageEndpoint == null) {
            initializeMcpSession();
        }
    }

    private synchronized void initializeMcpSession() {
        if (verifiedMessageEndpoint != null) return;

        try {
            System.out.println("[DEBUGGING] Attempting connection to SSE stream at " + BASE_URL + "/sse...");
            HttpRequest sseRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/sse"))
                    .GET()
                    .build();

            httpClient.sendAsync(sseRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println("[SERVER ERROR] SSE stream returned unexpected HTTP status: " + response.statusCode());
                            return;
                        }

                        try (InputStream is = response.body();
                             InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {

                            StringBuilder lineBuffer = new StringBuilder();
                            boolean nextLineIsEndpointData = false;
                            int c;

                            while ((c = isr.read()) != -1) {
                                if (c == '\n' || c == '\r') {
                                    String trimmed = lineBuffer.toString().trim();
                                    lineBuffer.setLength(0);

                                    if (trimmed.isEmpty()) continue;
                                    System.out.println("[RAW WIRE INBOUND] -> " + trimmed);

                                    if (trimmed.startsWith("event: endpoint")) {
                                        nextLineIsEndpointData = true;
                                        continue;
                                    }

                                    if (nextLineIsEndpointData && trimmed.startsWith("data:")) {
                                        nextLineIsEndpointData = false;
                                        verifiedMessageEndpoint = trimmed.substring(5).trim();
                                        System.out.println("🎯 [PARSED ROUTE SYSTEM LIVE] -> " + verifiedMessageEndpoint);

                                        sendMcpHandshake(verifiedMessageEndpoint);
                                        continue;
                                    }

                                    if (trimmed.startsWith("data:") && trimmed.contains("\"id\":")) {
                                        String rawJson = trimmed.substring(trimmed.indexOf("{")).trim();

                                        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"?(\\d+)\"?");
                                        java.util.regex.Matcher idMatcher = idPattern.matcher(rawJson);

                                        if (idMatcher.find()) {
                                            String msgId = idMatcher.group(1);
                                            CompletableFuture<String> future = pendingRequests.remove(msgId);
                                            if (future != null) {
                                                future.complete(rawJson);
                                            }
                                        }
                                    }
                                } else {
                                    lineBuffer.append((char) c);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[STREAM ERROR] Disconnected from live context: " + e.getMessage());
                        }
                    })
                    // catch mcp fail
                    .exceptionally(ex -> {
                        System.err.println("[CONNECTION FAILURE] MCP server is not accepting connections on port 8080 yet. (Will auto-retry on tool interaction).");
                        return null;
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMcpHandshake(String endpointPath) {
        try {
            String handshakePayload = "{"
                    + "\"jsonrpc\":\"2.0\","
                    + "\"method\":\"initialize\","
                    + "\"params\":{"
                    + "  \"protocolVersion\":\"2024-11-05\","
                    + "  \"capabilities\":{},"
                    + "  \"clientInfo\":{\"name\":\"JavaFX-Client\",\"version\":\"1.0.0\"}"
                    + "},"
                    + "\"id\":0"
                    + "}";

            String fullTargetUrl = BASE_URL + (endpointPath.startsWith("/") ? "" : "/") + endpointPath;
            System.out.println("🚀 [HANDSHAKE OUTBOUND] URL: " + fullTargetUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullTargetUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(handshakePayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        System.out.println("[HANDSHAKE RESPONSE] HTTP Status: " + res.statusCode());
                        if (res.statusCode() == 200 || res.statusCode() == 202) {
                            sendMcpInitializedNotification(fullTargetUrl);
                        }
                    });
        } catch (Exception e) {
            System.err.println("Handshake failed: " + e.getMessage());
        }
    }

    private void sendMcpInitializedNotification(String fullTargetUrl) {
        try {
            String initializedNotificationPayload = "{"
                    + "\"jsonrpc\":\"2.0\","
                    + "\"method\":\"notifications/initialized\""
                    + "}";

            System.out.println("[INITIALIZED NOTIFICATION OUTBOUND] -> " + initializedNotificationPayload);

            HttpRequest notificationRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fullTargetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(initializedNotificationPayload))
                    .build();

            httpClient.sendAsync(notificationRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> System.out.println("✅ [SESSION FULLY LIVE] Server accepted notification state: " + res.statusCode()));

        } catch (Exception e) {
            System.err.println("Failed sending initialized notification: " + e.getMessage());
        }
    }

    private void ensureEndpointIsReady() {
        int maxAttempts = 12; // server timeout
        while (verifiedMessageEndpoint == null && maxAttempts > 0) {
            System.out.println("⚠️ [MCP CLIENT] Message channel uninitialized. Polling execution hook... (Attempts remaining: " + maxAttempts + ")");
            initializeMcpSession();
            try {
                TimeUnit.MILLISECONDS.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            maxAttempts--;
        }
    }

    public String executeToolCall(String toolName, String argumentJson) {
        ensureEndpointIsReady();

        if (verifiedMessageEndpoint == null) {
            return "{\"error\":\"Client is uninitialized. Server did not bind to communication endpoint lines.\"}";
        }

        long numericId = System.currentTimeMillis() % 1000000L;
        String requestId = String.valueOf(numericId);

        String payload = "{"
                + "\"jsonrpc\":\"2.0\","
                + "\"method\":\"tools/call\","
                + "\"params\":{"
                + "  \"name\":\"" + toolName + "\","
                + "  \"arguments\":" + argumentJson
                + "},"
                + "\"id\":" + requestId
                + "}";

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            String fullTargetUrl = BASE_URL + (verifiedMessageEndpoint.startsWith("/") ? "" : "/") + verifiedMessageEndpoint;
            System.out.println("🤖 [TOOL OUTBOUND] URL: " + fullTargetUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullTargetUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(postResponse -> {
                        if (postResponse.statusCode() != 200 && postResponse.statusCode() != 202) {
                            CompletableFuture<String> future = pendingRequests.remove(requestId);
                            if (future != null) {
                                future.complete("{\"error\":\"Server rejected request with status: " + postResponse.statusCode() + "\"}");
                            }
                        }
                    });

            return responseFuture.get(8, TimeUnit.SECONDS);

        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return "{\"error\":\"Timeout waiting for matching downstream SSE frame.\"}";
        }
    }

    public String searchCampusInfo(String query) {
        String argumentJson = "{\"query\":\"" + query.replace("\"", "\\\"") + "\"}";
        try {
            return executeToolCall("search_campus_info", argumentJson);
        } catch (Exception e) {
            return "{\"error\":\"Internal UI processing error.\"}";
        }
    }

    public String fetchLeaveTemplate(String leaveType) {
        ensureEndpointIsReady();

        if (verifiedMessageEndpoint == null) {
            return "Fallback Template Layout:\n\nPlease accept this formal request for " + leaveType + " leave.";
        }

        long numericId = (System.currentTimeMillis() % 1000000L) + 1;
        String requestId = String.valueOf(numericId);

        String payload = "{"
                + "\"jsonrpc\":\"2.0\","
                + "\"method\":\"prompts/get\","
                + "\"params\":{"
                + "  \"name\":\"draft_leave_request\","
                + "  \"arguments\":{\"type\":\"" + leaveType + "\"}"
                + "},"
                + "\"id\":" + requestId
                + "}";

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            String fullTargetUrl = BASE_URL + (verifiedMessageEndpoint.startsWith("/") ? "" : "/") + verifiedMessageEndpoint;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullTargetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            return responseFuture.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return "Fallback Template Layout:\n\nPlease accept this formal request for " + leaveType + " leave.";
        }
    }

    public String discoverServerTools() {
        ensureEndpointIsReady();

        if (verifiedMessageEndpoint == null) {
            System.err.println("❌ [MCP CLIENT] Discovery dropped: Target route is missing.");
            return null;
        }

        String requestId = String.valueOf(System.currentTimeMillis() % 1000000L);
        String payload = "{"
                + "\"jsonrpc\":\"2.0\","
                + "\"method\":\"tools/list\","
                + "\"id\":" + requestId
                + "}";

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            String fullTargetUrl = BASE_URL + (verifiedMessageEndpoint.startsWith("/") ? "" : "/") + verifiedMessageEndpoint;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullTargetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            return responseFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Failed to discover tools: " + e.getMessage());
            return null;
        }
    }

    public String executePromptLookup(String promptName, String jsonArgs) {
        try {
            String payload = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":999,\"method\":\"prompts/get\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}",
                    promptName,
                    jsonArgs
            );

            return payload;
        } catch (Exception e) {
            System.err.println("Failed to fetch server prompt: " + e.getMessage());
            return "";
        }
    }

}