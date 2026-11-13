package com.ascargon.rocketshow.lighting.designer;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Versions of profiles as the online designer describes them (VersionInfo of the fixture library), for the tests of
 * the library of the unit.
 */
final class LibraryVersions {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private LibraryVersions() {
    }

    // The only version of its fixture, from the Open Fixture Library, nobody voted on it yet
    static ObjectNode version(String uuid, String fixture, String manufacturerName, String name) {
        ObjectNode version = MAPPER.createObjectNode();
        version.put("uuid", uuid);
        version.put("fixture", fixture);
        version.put("manufacturerShortName", fixture.substring(0, fixture.indexOf('/')));
        version.put("manufacturerName", manufacturerName);
        version.put("name", name);
        version.put("mainCategory", "Moving Head");
        version.put("origin", "ofl");
        version.put("state", "open");
        version.put("confirmations", 0);
        version.put("problems", 0);
        version.put("usage", 0);
        version.put("versions", 1);
        version.put("best", true);
        version.put("bestUuid", uuid);
        version.put("currentOfl", true);
        version.put("hidden", false);
        version.put("retired", false);
        version.put("created", "2026-11-13T10:00:00Z");
        version.putNull("parentUuid");
        version.putNull("manual");
        ArrayNode modeStates = version.putArray("modeStates");
        modeStates.addObject()
                .put("shortName", "8ch")
                .put("name", "8-channel")
                .put("hash", "6d7fce9fee471194aa8b5b6e47267f03")
                .put("state", "open")
                .put("good", 0)
                .put("bad", 0);
        return version;
    }

    // The profile of a version
    static ObjectNode profile(String name) {
        ObjectNode profile = MAPPER.createObjectNode();
        profile.put("name", name);
        profile.putArray("categories").add("Moving Head");
        profile.putArray("modes");
        return profile;
    }

}
