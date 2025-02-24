package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.ControlAction;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated Not used since settings version 3 anymore. Use ActionTrigger instead.
 */
@Deprecated
@Getter
@Setter
public class MidiControl extends ControlAction {

    // If null -> all channels
    private Integer channelFrom;

    // The note, this action should be triggered. If null -> all notes
    private Integer noteFrom;

}
