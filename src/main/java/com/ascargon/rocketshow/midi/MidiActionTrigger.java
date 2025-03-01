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
        @JsonSubTypes.Type(value = MidiActionTriggerNoteOn.class, name = "midiActionTriggerNoteOn"),
        @JsonSubTypes.Type(value = MidiActionTriggerProgramChange.class, name = "midiActionTriggerProgramChange"),
})
@Getter
@Setter
public class MidiActionTrigger extends ActionTrigger {

    public enum MidiActionTriggerType {
        NOTE_ON,
        PROGRAM_CHANGE
    }

    private MidiActionTriggerType midiActionTriggerType;

    // If null -> all channels
    private Integer channel;

}
