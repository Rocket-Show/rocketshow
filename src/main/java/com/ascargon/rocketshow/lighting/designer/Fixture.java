package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer fixture.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fixture {

    private String uuid;
    private String fixtureTemplateUuid;
    private String name;
    private String dmxUniverseUuid = "";
    private int dmxFirstChannel;
    private String modeShortName;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getFixtureTemplateUuid() {
        return fixtureTemplateUuid;
    }

    public void setFixtureTemplateUuid(String fixtureTemplateUuid) {
        this.fixtureTemplateUuid = fixtureTemplateUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDmxUniverseUuid() {
        return dmxUniverseUuid;
    }

    public void setDmxUniverseUuid(String dmxUniverseUuid) {
        this.dmxUniverseUuid = dmxUniverseUuid;
    }

    public int getDmxFirstChannel() {
        return dmxFirstChannel;
    }

    public void setDmxFirstChannel(int dmxFirstChannel) {
        this.dmxFirstChannel = dmxFirstChannel;
    }

    public String getModeShortName() {
        return modeShortName;
    }

    public void setModeShortName(String modeShortName) {
        this.modeShortName = modeShortName;
    }
}
