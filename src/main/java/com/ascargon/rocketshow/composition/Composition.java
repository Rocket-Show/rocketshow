package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Getter
@Setter
public class Composition {

    private String name;
    private String notes;
    private long durationMillis;
    private boolean loop = false;

    // The absolute MIDI timecode position (in milliseconds) at which this composition starts, used
    // when following an incoming MIDI timecode with the per-composition mapping.
    private long timecodeStartMillis = 0;

    // The number that selects this composition over MIDI (program change or song select), counted
    // from 1. Null if this composition cannot be selected over MIDI.
    private Integer midiNumber;

    // The MIDI Show Control cue number that selects this composition, e.g. "12.5". Empty to use the
    // MIDI number instead.
    private String showControlCue;
    private List<CompositionFile> compositionFileList = new ArrayList<>();
    private List<LeadSheet> leadSheetList = new ArrayList<>();
    private float audioVolume = 1;
    private List<ActionTriggerComposition> actionTriggerList = new ArrayList<>();

    @XmlElementWrapper(name = "fileList")
    @XmlElements({@XmlElement(type = MidiCompositionFile.class, name = "midiFile"),
            @XmlElement(type = VideoCompositionFile.class, name = "videoFile"),
            @XmlElement(type = AudioCompositionFile.class, name = "audioFile")})
    @JsonProperty("fileList")
    @SuppressWarnings("WeakerAccess")
    public List<CompositionFile> getCompositionFileList() {
        return compositionFileList;
    }

}
