package org.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.model.LeaveApplication;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;

public class LeaveRecordService {

    private static final String CSV_FILE_PATH = "student_leaves.csv";

    /**
     * Reads the entire CSV data file, respects multiline blocks wrapped in quotes,
     * and filters rows matching the target student session ID.
     */
    public ObservableList<LeaveApplication> fetchRecordsForStudent(String targetStudentId) {
        ObservableList<LeaveApplication> records = FXCollections.observableArrayList();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH))) {
            String line;
            StringBuilder multiLineBuffer = new StringBuilder();
            boolean insideQuotes = false;

            while ((line = br.readLine()) != null) {
                if (insideQuotes) {
                    multiLineBuffer.append("\n").append(line);
                } else {
                    multiLineBuffer.setLength(0);
                    multiLineBuffer.append(line);
                }

                // check unescaped quotes
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '"') {
                        insideQuotes = !insideQuotes;
                    }
                }

                if (!insideQuotes) {
                    LeaveApplication app = parseCsvLine(multiLineBuffer.toString());
                    if (app != null && targetStudentId.equals(app.getStudentId())) {
                        records.add(app);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read leave histories: " + e.getMessage());
        }

        return records;
    }

    private LeaveApplication parseCsvLine(String fullRow) {
        try {
            String[] columns = fullRow.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (columns.length < 6) return null;

            String studentId = columns[0].trim();
            String leaveType = columns[1].trim();
            LocalDate startDate = LocalDate.parse(columns[2].trim());
            LocalDate endDate = LocalDate.parse(columns[3].trim());

            String reason = columns[4].trim();
            if (reason.startsWith("\"") && reason.endsWith("\"")) {
                reason = reason.substring(1, reason.length() - 1).replace("\"\"", "\"");
            }

            String referenceCode = columns[5].trim();

            return new LeaveApplication(studentId, leaveType, startDate, endDate, reason, referenceCode);
        } catch (Exception e) {
            System.err.println("Skipping unparseable row entry: " + e.getMessage());
            return null;
        }
    }
}