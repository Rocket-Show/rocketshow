package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ActionLighting extends Action {

    private List<LightingActionUniverse> lightingActionUniverseList = new ArrayList<>();

    @Override
    public ActionType getType() {
        return ActionType.LIGHTING;
    }
}
