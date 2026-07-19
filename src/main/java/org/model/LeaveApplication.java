package org.model;

import java.time.LocalDate;

public class LeaveApplication {
    private final String studentId;
    private final String leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String reason;
    private final String referenceCode;

    public LeaveApplication(String studentId, String leaveType, LocalDate startDate, LocalDate endDate, String reason, String referenceCode) {
        this.studentId = studentId;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.referenceCode = referenceCode;
    }


    public String getStudentId() {
        return studentId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    /**
     * Converts the object data into a clean, comma-separated row line for CSV storage.
     * Sanitizes strings to prevent commas inside inputs from shifting column cells.
     */
    public String toCsvRow() {
        return String.join(",",
                studentId,
                leaveType,
                startDate.toString(),
                endDate.toString(),
                "\"" + reason.replace("\"", "\"\"") + "\"",
                referenceCode
        );
    }
}