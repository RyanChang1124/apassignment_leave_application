package org.service;

import java.util.concurrent.CompletableFuture;

public interface ChatService {
    /**
     * Sends a message to the assistant and returns the response asynchronously.
     * This ensures the JavaFX UI thread never blocks during network I/O.
     */
    CompletableFuture<String> sendMessage(String userMessage);

    /**
     * Clears the current session history and resets the conversation context.
     */
    void clearHistory();
}