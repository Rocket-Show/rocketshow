package com.ascargon.rocketshow.lighting.designer;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public interface FixtureService {

    void invalidateCache();

    List<SearchFixtureTemplate> searchFixtures(String uuid, String manufacturerShortName, String name, String mainCategory) throws IOException;

    String getFixture(String uuid) throws IOException;

}
