package org.service;

import org.storage.StorageEngine;

public class ValidationService {
    /**
     * Checks if inputs are structurally valid and delegates verification to the storage engine.
     */
    public boolean validateStudentCredentials(String id, String name) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            return false;
        }

        return StorageEngine.verifyStudentExists(id.trim(), name.trim());
    }
}
