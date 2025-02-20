package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.ControlAction;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Map one specific MIDI event to an action.
 *
 * @author Moritz Vieli
 */
@XmlRootElement
@Getter
@Setter
public class MidiControl extends ControlAction {

    // If null -> all channels
    private Integer channelFrom;

    // The note, this action should be triggered. If null -> all notes
    private Integer noteFrom;

}
