package com.campuscompanion.service;

import java.util.List;

public interface CampusDataService {
    /**
     * Directly queries the fallback/MCP data store for campus information.
     */
    List<String> searchCampusInfo(String query);

    /**
     * Submits a booking request for a campus facility.
     */
    boolean bookFacility(String facilityId, String timeslot, String studentId);
}