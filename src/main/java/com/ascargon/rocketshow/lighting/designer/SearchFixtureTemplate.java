package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A fixture template returned as a search result.
 *
 * @author Moritz A. Vieli
 */
public class SearchFixtureTemplate {

    private String uuid;
    private String name;
    private String manufacturerShortName;
    private String manufacturerName;

    private String mainCategory;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturerShortName() {
        return manufacturerShortName;
    }

    public void setManufacturerShortName(String manufacturerShortName) {
        this.manufacturerShortName = manufacturerShortName;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getMainCategory() {
        return mainCategory;
    }

    public void setMainCategory(String mainCategory) {
        this.mainCategory = mainCategory;
    }

}
