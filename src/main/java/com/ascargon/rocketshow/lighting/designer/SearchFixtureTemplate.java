package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A version of a fixture profile as the search of the designer gets it: what the library knows about it, the same as
 * the API of the online designer answers (VersionInfo of the fixture library). The designer merges it with the profile
 * when it adds the profile to a project.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
// The fields the API answers as null are null here as well
@JsonInclude(JsonInclude.Include.ALWAYS)
// Fields of a newer library don't keep the others from being read
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchFixtureTemplate {

    // A manual a version was made from: the public address it was read from, or the name of the file
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Manual {
        private String url;
        private String name;
    }

    // A mode of the version: what it does (its hash, the same in every version which has the mode unchanged), and what
    // the votes on it say
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModeState {
        private String shortName;
        private String name;
        private String hash;
        private String state;
        private Integer good;
        private Integer bad;
    }

    // The uuid of the version, "<manufacturer>/<fixture-key>~<version id>", or the plain uuid of a profile of the
    // library downloaded as a zip until the sync replaced it
    private String uuid;

    // The plain uuid of the fixture of the version, as the fixture is named now
    private String fixture;

    private String manufacturerShortName;
    private String manufacturerName;
    private String name;
    private String mainCategory;

    // ofl, ai or user
    private String origin;

    // confirmed, open or problem
    private String state;

    // The accounts with a good, respectively a bad vote on one of its modes
    private Integer confirmations;
    private Integer problems;

    private Integer usage;

    // The versions of its fixture which aren't hidden
    private Integer versions;

    // Whether it is the best version of its fixture, and which one that is
    private Boolean best;
    private String bestUuid;

    // Whether it is the current version of the Open Fixture Library
    private Boolean currentOfl;

    private Boolean hidden;
    private Boolean retired;

    // ISO 8601, UTC
    private String created;

    private String parentUuid;
    private Manual manual;
    private List<ModeState> modeStates;

}
