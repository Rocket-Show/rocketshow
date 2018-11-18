package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.CompositionFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import org.freedesktop.gstreamer.Pipeline;

public class MidiCompositionFile extends CompositionFile {

    private List<MidiRouting> midiRoutingList = new ArrayList<>();

    @XmlElement(name = "midiRouting")
    @XmlElementWrapper(name = "midiRoutingList")
    public List<MidiRouting> getMidiRoutingList() {
        return midiRoutingList;
    }

    public void setMidiRoutingList(List<MidiRouting> midiRoutingList) {
        this.midiRoutingList = midiRoutingList;
    }

}
