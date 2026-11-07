package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionTransport extends Action {

    public enum TransportActionType {
        PLAY,
        PLAY_AS_SAMPLE,
        TOGGLE_PLAY,
        PAUSE,
        NEXT_COMPOSITION,
        PREVIOUS_COMPOSITION,
        STOP,
        SELECT_COMPOSITION_BY_NAME,
        SELECT_COMPOSITION_BY_NAME_AND_PLAY,
        // on from the hold point the composition holds at, else play it
        GO,
    }

    private TransportActionType transportActionType;
    private String compositionName;

    @Override
    public ActionType getType() {
        return ActionType.TRANSPORT;
    }

}
