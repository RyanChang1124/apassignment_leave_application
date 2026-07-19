package org.service;

import org.model.LeaveApplication;
import org.storage.StorageEngine;
import java.time.LocalDate;
import java.util.UUID;

public class LeaveService {

    /**
     * Validates input structural constraints, mocks a transactional reference tracking code,
     * and writes the record permanently to the text log file.
     */
    public String submitLeaveRequest(String studentId, String leaveType, LocalDate start, LocalDate end, String reason) throws IllegalArgumentException {
        if (studentId == null || studentId.isBlank()) throw new IllegalArgumentException("Student authentication token missing.");
        if (leaveType == null || leaveType.isBlank()) throw new IllegalArgumentException("Please pick a valid classification type.");
        if (start == null || end == null) throw new IllegalArgumentException("Operational timeline bounds cannot be blank.");
        if (end.isBefore(start)) throw new IllegalArgumentException("End Date cannot fall prior to the Start Date bounds.");
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("Please provide a distinct reason (minimum 10 characters).");

        String dateStamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now());
        int randomSuffix = (int) (Math.random() * 9000) + 1000; // Guarantees a clean 4-digit block
        String referenceCode = "REF-" + dateStamp + "-" + randomSuffix;

        LeaveApplication application = new LeaveApplication(studentId, leaveType, start, end, reason.trim(), referenceCode);

        StorageEngine.appendLeaveRecord(application.toCsvRow());

        return referenceCode;
    }
}