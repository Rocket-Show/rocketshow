package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A folder of the scene tree of the designer. Folders only group the scenes: the scenes stay in the list of the
 * project and carry the uuid of the folder they are in. Folders and scenes of the same parent are ordered together by
 * their sortIndex.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class SceneFolder {

    private String uuid;
    private String name;

    // the folder this one is in (null = top level)
    private String parentUuid;

    // position among the folders and scenes of the same parent
    private double sortIndex = 0;

}
