package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.Midi2LightingMapping;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines, where to route the output of MIDI signals.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
public class MidiRouting {

    private MidiDestination midiDestination = MidiDestination.OUT_DEVICE;

    private MidiMapping midiMapping = new MidiMapping();
    private Midi2LightingMapping midi2LightingMapping = new Midi2LightingMapping();
    private String universeUuid;

    // A list of remote device ids in case of destination type = REMOTE
    private List<String> remoteDeviceNameList = new ArrayList<>();

    @XmlElement(name = "remoteDevice")
    @XmlElementWrapper(name = "remoteDeviceList")
    public List<String> getRemoteDeviceIdList() {
        return remoteDeviceNameList;
    }

}
