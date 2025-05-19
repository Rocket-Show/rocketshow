package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.ActionTrigger;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActionTriggerMidiNoteOn.class, name = "actionTriggerMidiNoteOn"),
        @JsonSubTypes.Type(value = ActionTriggerMidiProgramChange.class, name = "actionTriggerMidiProgramChange"),
})
@Getter
@Setter
public class ActionTriggerMidi extends ActionTrigger {

    // If null -> all channels
    private Integer channel;

}
