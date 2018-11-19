package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.dmx.Midi2DmxMapping;

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

    public enum MidiDestination {
        OUT_DEVICE, DMX, REMOTE
    }

    private MidiDestination midiDestination = MidiDestination.OUT_DEVICE;

    private MidiMapping midiMapping = new MidiMapping();
    private Midi2DmxMapping midi2DmxMapping = new Midi2DmxMapping();

    // A list of remote device ids in case of destination type = REMOTE
    private List<String> remoteDeviceNameList = new ArrayList<>();

    @XmlElement
    public MidiDestination getMidiDestination() {
        return midiDestination;
    }

    public void setMidiDestination(MidiDestination midiDestination) {
        this.midiDestination = midiDestination;
    }

    @XmlElement
    public Midi2DmxMapping getMidi2DmxMapping() {
        return midi2DmxMapping;
    }

    public void setMidi2DmxMapping(Midi2DmxMapping midi2DmxMapping) {
        this.midi2DmxMapping = midi2DmxMapping;
    }

    @XmlElement(name = "remoteDevice")
    @XmlElementWrapper(name = "remoteDeviceList")
    public List<String> getRemoteDeviceIdList() {
        return remoteDeviceNameList;
    }

    public void setRemoteDeviceIdList(List<String> remoteDeviceNameList) {
        this.remoteDeviceNameList = remoteDeviceNameList;
    }

    @XmlElement
    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

}
