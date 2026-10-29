package com.ascargon.rocketshow.session;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Getter
@Setter
public class Session {

    private String currentSetName;
    private boolean autoSelectNextComposition = false;
    private String playViewMode = "setlist";

    // what the Play screen shows: the compositions of the set ("compositions") or the scenes of the designer projects
    // ("scenes")
    private String playMode = "compositions";

    // the scenes picked by hand on the Play screen (see ScenePlayerService), so they light the stage again after a
    // restart: the designer project they are from, the scenes, a blackout holding them dark and the master dimmer of
    // the Play screen (0 to 1)
    private String sceneProjectName;
    private List<String> activeSceneUuids = new ArrayList<>();
    private boolean sceneBlackout = false;
    private double sceneMasterDimmer = 1;

}
