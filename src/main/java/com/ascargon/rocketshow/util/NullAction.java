package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NullAction extends Action {

    @Override
    public ActionType getType() {
        return ActionType.NULL;
    }
}
