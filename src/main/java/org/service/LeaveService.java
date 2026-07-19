package org.service;

import org.model.LeaveApplication;
import org.storage.StorageEngine;
import org.mcp.McpClientService;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeaveService {

    private final McpClientService mcpService = new McpClientService();

    public String submitLeaveRequest(String studentId, String leaveType, LocalDate start, LocalDate end, String reason) throws IllegalArgumentException {
        // validation
        if (studentId == null || studentId.isBlank()) throw new IllegalArgumentException("Student authentication token missing.");
        if (leaveType == null || leaveType.isBlank()) throw new IllegalArgumentException("Please pick a valid classification type.");
        if (start == null || end == null) throw new IllegalArgumentException("Operational timeline bounds cannot be blank.");
        if (end.isBefore(start)) throw new IllegalArgumentException("End Date cannot fall prior to the Start Date bounds.");
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("Please provide a distinct reason (minimum 10 characters).");

        String fromDate = start.toString();
        String toDate = end.toString();
        String escapedReason = reason.trim().replace("\\", "\\\\").replace("\"", "\\\"");

        String jsonArgs = String.format(
                "{\"studentId\":\"%s\",\"fromDate\":\"%s\",\"toDate\":\"%s\",\"reason\":\"%s\"}",
                studentId.replace("\"", "\\\""),
                fromDate,
                toDate,
                escapedReason
        );

        System.out.println("sending transaction to MCP Tool [submit_leave_application]...");
        String serverResponse = mcpService.executeToolCall("submit_leave_application", jsonArgs);

        if (serverResponse == null || serverResponse.isBlank()) {
            throw new RuntimeException("Server communication failure: Received empty response.");
        }

        //
        if (serverResponse.contains("\"isError\":true") || serverResponse.contains("\"error\"")) {
            throw new RuntimeException("Server explicitly rejected tool interaction: " + serverResponse);
        }

        // get text from server response json
        String getText = serverResponse;
        if (serverResponse.contains("\"text\":\"")) {
            int startIndex = serverResponse.indexOf("\"text\":\"") + 8;
            int endIndex = serverResponse.indexOf("\"", startIndex);
            if (startIndex > 7 && endIndex > startIndex) {
                getText = serverResponse.substring(startIndex, endIndex);
            }
        }

        // get reference code
        String referenceCode = extractReferenceCode(getText);

        // append to csv
        LeaveApplication application = new LeaveApplication(studentId, leaveType, start, end, reason.trim(), referenceCode);
        StorageEngine.appendLeaveRecord(application.toCsvRow());

        return getText;
    }

    private String extractReferenceCode(String text) {
        Pattern pattern = Pattern.compile("Reference\\s+(\\S+?)(?:\\.|\\s|$)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "REF-" + System.currentTimeMillis();
    }
}