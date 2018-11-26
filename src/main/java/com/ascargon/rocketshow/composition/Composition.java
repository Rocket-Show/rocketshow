package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class Composition {

    private String name;

    private String notes;

    private long durationMillis;

    private List<CompositionFile> compositionFileList = new ArrayList<>();

    private boolean autoStartNextComposition = false;

    @XmlElementWrapper(name = "fileList")
    @XmlElements({@XmlElement(type = MidiCompositionFile.class, name = "midiFile"),
            @XmlElement(type = VideoCompositionFile.class, name = "videoFile"),
            @XmlElement(type = AudioCompositionFile.class, name = "audioFile")})
    @JsonSubTypes({ @JsonSubTypes.Type(name = "midi", value = MidiCompositionFile.class) })
    public List<CompositionFile> getCompositionFileList() {
        return compositionFileList;
    }

    public void setCompositionFileList(List<CompositionFile> compositionFileList) {
        this.compositionFileList = compositionFileList;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    @XmlElement
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @XmlElement
    public boolean isAutoStartNextComposition() {
        return autoStartNextComposition;
    }

    public void setAutoStartNextComposition(boolean autoStartNextComposition) {
        this.autoStartNextComposition = autoStartNextComposition;
    }
}
