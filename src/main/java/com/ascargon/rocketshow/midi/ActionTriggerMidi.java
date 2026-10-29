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
        @JsonSubTypes.Type(value = ActionTriggerMidiControlChange.class, name = "actionTriggerMidiControlChange"),
        @JsonSubTypes.Type(value = ActionTriggerMidiSongSelect.class, name = "actionTriggerMidiSongSelect"),
        @JsonSubTypes.Type(value = ActionTriggerMidiSystemRealTime.class, name = "actionTriggerMidiSystemRealTime"),
})
@Getter
@Setter
public class ActionTriggerMidi extends ActionTrigger {

    // If null -> all channels. Ignored for system messages (song select, transport), which carry no
    // channel at all.
    private Integer channel;

}
