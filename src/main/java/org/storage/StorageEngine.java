package org.storage;

import java.io.*;

public class StorageEngine {
    // paths
    private static final String STUDENT_REGISTRY_FILE = "students.txt";
    private static final String LEAVE_LOG_FILE = "student_leaves.csv";

    /**
     * Scans students.txt line-by-line using a thread-safe stream.
     * Expected file format: ID, Full Name (e.g., 0385084, Ryan Chang)
     */
    public static synchronized boolean verifyStudentExists(String inputId, String inputName) {
        File file = new File(STUDENT_REGISTRY_FILE);
        if (!file.exists()) {
            System.err.println("Database Warning: " + STUDENT_REGISTRY_FILE + " is missing from the project root.");
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split by comma to separate ID and Name
                String[] tokens = line.split(",", 2);
                if (tokens.length == 2) {
                    String fileId = tokens[0].trim();
                    String fileName = tokens[1].trim();

                    // case insensitive comparison
                    if (fileId.equalsIgnoreCase(inputId) && fileName.equalsIgnoreCase(inputName)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Disk Error while scanning student registry: " + e.getMessage());
        }
        return false;
    }

    /**
     * Safely appends a raw CSV record row to student_leaves.csv.
     */
    public static synchronized void appendLeaveRecord(String csvRow) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LEAVE_LOG_FILE, true))) {
            writer.write(csvRow);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Disk Error while appending leave record: " + e.getMessage());
        }
    }
    // DEBUGGING
    public static void main(String[] args) {
        System.out.println("--- Testing Storage Engine Local Path Resolution ---");

        boolean matchFound = verifyStudentExists("0385084", "Ryan Chang");
        System.out.println("Verification test (Valid Match): " + (matchFound ? "PASS ✅" : "FAIL ❌"));

        boolean badMatch = verifyStudentExists("9999999", "Imposter Student");
        System.out.println("Verification test (Invalid Match): " + (!badMatch ? "PASS ✅" : "FAIL ❌"));
    }
}