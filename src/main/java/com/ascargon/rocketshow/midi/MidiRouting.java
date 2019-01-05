package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.Midi2LightingMapping;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines, where to route the output of MIDI signals.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class MidiRouting {

    private MidiSignal.MidiDestination midiDestination = MidiSignal.MidiDestination.OUT_DEVICE;

    private MidiMapping midiMapping = new MidiMapping();
    private Midi2LightingMapping midi2LightingMapping = new Midi2LightingMapping();

    // A list of remote device ids in case of destination type = REMOTE
    private List<String> remoteDeviceNameList = new ArrayList<>();

    public MidiSignal.MidiDestination getMidiDestination() {
        return midiDestination;
    }

    public void setMidiDestination(MidiSignal.MidiDestination midiDestination) {
        this.midiDestination = midiDestination;
    }

    public Midi2LightingMapping getMidi2LightingMapping() {
        return midi2LightingMapping;
    }

    public void setMidi2LightingMapping(Midi2LightingMapping midi2LightingMapping) {
        this.midi2LightingMapping = midi2LightingMapping;
    }

    @XmlElement(name = "remoteDevice")
    @XmlElementWrapper(name = "remoteDeviceList")
    public List<String> getRemoteDeviceIdList() {
        return remoteDeviceNameList;
    }

    public void setRemoteDeviceIdList(List<String> remoteDeviceNameList) {
        this.remoteDeviceNameList = remoteDeviceNameList;
    }

    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

}
