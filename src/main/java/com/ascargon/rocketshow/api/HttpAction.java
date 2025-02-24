package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HttpAction extends Action {

    // TODO

    @Override
    public ActionType getType() {
        return ActionType.HTTP;
    }
}
