package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionSystem extends Action {

    public enum SystemActionType {
        REBOOT
    }

    private SystemActionType systemActionType;

    @Override
    public ActionType getType() {
        return ActionType.SYSTEM;
    }
}
