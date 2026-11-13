package com.ascargon.rocketshow.lighting.designer;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface FixtureService {

    // The search of the designer, the same as the one of the API of the online designer: the version with the passed
    // uuid, the versions of the passed fixture, or else the best version of each fixture, each with the passed filters
    List<SearchFixtureTemplate> searchFixtures(String uuid, String fixture, String manufacturerShortName, String name, String mainCategory) throws Exception;

    // The profile of the version with the passed uuid, null if the library has none
    String getFixture(String uuid) throws Exception;

    // Syncs the library with the online designer and waits for it
    void updateProfiles() throws Exception;

    // Syncs the library in the background, shortly after the start and then every day
    void startBackgroundSync();

}
