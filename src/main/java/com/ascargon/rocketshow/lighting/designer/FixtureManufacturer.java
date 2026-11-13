package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A manufacturer of the fixture library, as the designer gets it (e.g. to choose the manufacturer of a profile the AI
 * generates), the same as from the API of the online designer.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FixtureManufacturer(String shortName, String name, String website) {
}
