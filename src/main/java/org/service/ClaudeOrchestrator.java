package org.service;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mcp.McpClientService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ClaudeOrchestrator {
    private static final String ANTHROPIC_API_KEY = System.getenv("ANTHROPIC_API_KEY");
    private static final String CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private final HttpClient client;
    private final McpClientService mcpService;

    // conversation history
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();

    public ClaudeOrchestrator() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mcpService = new McpClientService();
    }

    public String generateDraftFromHistory(String studentName, String studentId, String leaveType, String startDate, String endDate) {
        if (ANTHROPIC_API_KEY == null || ANTHROPIC_API_KEY.isBlank() || ANTHROPIC_API_KEY.equals("YOUR_CLAUDE_API_KEY_HERE")) {
            return "Mock Mode Letter Body:\nDear Programme Office,\n\nI am writing to formally request " + leaveType
                    + " from " + startDate + " to " + endDate + ".\n\nSincerely,\n" + studentName + " (" + studentId + ")";
        }

        try {
            List<String> operationalHistory = new ArrayList<>(messageHistory);

            if (operationalHistory.isEmpty()) {
                operationalHistory.add("{\"role\":\"user\",\"content\":\"Hello, I need assistance tracking campus policy variables.\"}");
                operationalHistory.add("{\"role\":\"assistant\",\"content\":\"I am ready to review your files and policies.\"}");
            }

            // prompt integration
            // reason is pointed from chat history
            String contextualReason = "Detailed personal situation discussed in the attached conversation history context.";

            String promptArgs = String.format(
                    "{\"studentName\":\"%s\",\"fromDate\":\"%s\",\"toDate\":\"%s\",\"reason\":\"%s\"}",
                    studentName.replace("\"", "\\\""),
                    startDate,
                    endDate,
                    contextualReason.replace("\"", "\\\"")
            );

            System.out.println("Fetching server prompt template [draft_leave_request]...");

            // invoking prompt from mcp
            String serverPromptResponse = mcpService.executePromptLookup("draft_leave_request", promptArgs);
            String serverTemplateText = extractTextFromPromptResponse(serverPromptResponse);

            // generate template
            String draftingInstruction = "System Action: The user has finalized their application parameters.\n"
                    + "Base your response text layout on this official server prompt template:\n"
                    + "\"\"\"\n" + serverTemplateText + "\n\"\"\"\n\n"
                    + "Additional Parameters:\n"
                    + "- Student ID: " + studentId + "\n"
                    + "- Leave Type: " + leaveType + "\n\n"
                    + "Task: Finalize the formal leave request email addressed to the Academic Programme Office. "
                    + "Analyze the full chat conversation history above to extract the personal context/situations "
                    + "and merge them seamlessly into the server template layout. Do not use generic placeholders or bracketed markers. "
                    + "Return ONLY the completed text body of the email. Do not add casual chat commentary before or after the letter.";

            operationalHistory.add("{\"role\":\"user\",\"content\":\"" + draftingInstruction.replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
            String conversationBlocks = String.join(",", operationalHistory);

            StringBuilder payloadBuilder = new StringBuilder();
            payloadBuilder.append("{")
                    .append("\"model\":\"claude-haiku-4-5\",")
                    .append("\"max_tokens\":1536,")
                    .append("\"system\":\"You are a precise, executive administrative assistant. Your sole task is to output the final text of a formal student email draft based on history records and the server template. Output nothing else.\",")
                    .append("\"messages\":[").append(conversationBlocks).append("]");
            payloadBuilder.append("}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", ANTHROPIC_API_KEY)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadBuilder.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parseTextContent(response.body());

        } catch (Exception e) {
            return "Failed to synthesize context document framework via MCP Prompt: " + e.getMessage();
        }
    }

    public String chatWithGroundedContext(String studentQuery, String rawToolsJson) {
        if (ANTHROPIC_API_KEY == null || ANTHROPIC_API_KEY.isBlank() || ANTHROPIC_API_KEY.equals("YOUR_CLAUDE_API_KEY_HERE")) {
            String mockArgs = "{\"query\":\"" + studentQuery.replace("\"", "\\\"") + "\"}";
            return mcpService.executeToolCall("search_campus_info", mockArgs);
        }

        System.out.println("Secure API Key detected! Routing query directly to Claude...");

        String escapedQuery = studentQuery
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

        messageHistory.add("{\"role\":\"user\",\"content\":\"" + escapedQuery + "\"}");

        try {
            String conversationBlocks = String.join(",", messageHistory);

            StringBuilder payloadBuilder = new StringBuilder();
            payloadBuilder.append("{")
                    .append("\"model\":\"claude-haiku-4-5\",")
                    .append("\"max_tokens\":1024,")
                    .append("\"system\":\"You are a helpful campus policy assistant. Whenever a student asks about applying for leave or drafting an application letter, answer their policy questions using your tools, but explicitly remind them to select their 'Type of Leave' and choose their dates on the right-hand panel first, then click 'Pull Template via MCP' to generate their finished draft.\",")
                    .append("\"messages\":[").append(conversationBlocks).append("]");

            if (rawToolsJson != null) {
                payloadBuilder.append(",\"tools\":[{"
                        + "  \"name\":\"search_campus_info\","
                        + "  \"description\":\"Searches the official university campus database for regulations, rooms, or resources.\","
                        + "  \"input_schema\":{"
                        + "    \"type\":\"object\","
                        + "    \"properties\":{\"query\":{\"type\":\"string\",\"description\":\"The search term\"}},"
                        + "    \"required\":[\"query\"]"
                        + "  }"
                        + "}]");
            }
            payloadBuilder.append("}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", ANTHROPIC_API_KEY)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadBuilder.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (responseBody.contains("tool_use")) {
                System.out.println("🤖 Claude determined tool invocation is required...");

                String toolId = "call_default123";
                if (responseBody.contains("\"id\":\"")) {
                    int idStart = responseBody.indexOf("\"id\":\"") + 6;
                    int idEnd = responseBody.indexOf("\"", idStart);
                    toolId = responseBody.substring(idStart, idEnd);
                }

                String rawMcpResult = mcpService.executeToolCall("search_campus_info", "{\"query\":\"" + escapedQuery + "\"}");
                System.out.println("DEBUG - Raw MCP RAG Result contents: " + rawMcpResult);

                String escapedMcpResult = rawMcpResult
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "");

                messageHistory.add("{\"role\": \"assistant\", \"content\": [{\"type\": \"tool_use\", \"id\": \"" + toolId + "\", \"name\": \"search_campus_info\", \"input\": {\"query\": \"" + escapedQuery + "\"}}]}");

                return sendToolResultWithHistory(toolId, escapedMcpResult);
            }

            String cleanResponse = parseTextContent(responseBody);

            String escapedResponse = cleanResponse
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");

            messageHistory.add("{\"role\":\"assistant\",\"content\":\"" + escapedResponse + "\"}");
            return cleanResponse;

        } catch (Exception e) {
            return "Orchestration routing failure: " + e.getMessage();
        }
    }

    private String sendToolResultWithHistory(String toolId, String toolContent) {
        try {
            messageHistory.add("{\"role\": \"user\", \"content\": [{\"type\": \"tool_result\", \"tool_use_id\": \"" + toolId + "\", \"content\": \"" + toolContent + "\"}]}");

            String conversationBlocks = String.join(",", messageHistory);
            String followUpPayload = "{"
                    + "\"model\":\"claude-haiku-4-5\","
                    + "\"max_tokens\": 1024,"
                    + "\"messages\": [" + conversationBlocks + "]"
                    + "}";

            HttpRequest followUpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", ANTHROPIC_API_KEY)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(followUpPayload))
                    .build();

            HttpResponse<String> response = client.send(followUpRequest, HttpResponse.BodyHandlers.ofString());
            String rawJson = response.body();

            String cleanResponse = parseTextContent(rawJson);

            String escapedFollowUp = cleanResponse
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");

            messageHistory.add("{\"role\":\"assistant\",\"content\":\"" + escapedFollowUp + "\"}");
            return cleanResponse;
        } catch (Exception e) {
            return "Failed to send tool results to model generation layer: " + e.getMessage();
        }
    }

    private String parseTextContent(String rawJson) {
        if (!rawJson.contains("\"text\":\"")) {
            return rawJson;
        }

        try {
            int startIndex = rawJson.indexOf("\"text\":\"") + 8;
            StringBuilder sb = new StringBuilder();

            for (int i = startIndex; i < rawJson.length(); i++) {
                char current = rawJson.charAt(i);
                char next = (i + 1 < rawJson.length()) ? rawJson.charAt(i + 1) : '\0';

                if (current == '\\' && next == '"') {
                    sb.append('"');
                    i++;
                    continue;
                }

                if (current == '\\' && next == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }

                if (current == '"') {
                    break;
                }

                sb.append(current);
            }
            return sb.toString();
        } catch (Exception e) {
            System.out.println("Parsing exception fallback triggered: " + e.getMessage());
            return rawJson;
        }
    }

    // unwrap text from json
    private String extractTextFromPromptResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return "Draft a short, polite leave-request message to the programme office.";
        }
        if (responseJson.contains("\"text\":\"")) {
            int startIndex = responseJson.indexOf("\"text\":\"") + 8;
            int endIndex = responseJson.indexOf("\"", startIndex);
            if (startIndex > 7 && endIndex > startIndex) {
                return responseJson.substring(startIndex, endIndex);
            }
        }
        return responseJson;
    }

    public static void clearConversationHistory() {
        messageHistory.clear();
    }
}