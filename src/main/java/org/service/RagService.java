package org.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class RagService {
    private static final String HANDBOOK_FILE = "campus_handbook.txt";

    /**
     * Performs a local keyword search across the handbook to extract grounded rules.
     */
    public String retrieveRelevantContext(String userQuery) {
        File file = new File(HANDBOOK_FILE);
        if (!file.exists()) {
            return "No local campus policies found.";
        }

        StringBuilder contextBuilder = new StringBuilder();
        String lowerQuery = userQuery.toLowerCase();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // look for matching keywords
                String[] words = lowerQuery.split("\\s+");
                for (String word : words) {
                    if (word.length() > 3 && line.toLowerCase().contains(word)) {
                        contextBuilder.append(line).append("\n");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("RAG Engine Error reading context: " + e.getMessage());
        }

        return contextBuilder.length() > 0 ? contextBuilder.toString() : "Standard background policy constraints apply.";
    }
}