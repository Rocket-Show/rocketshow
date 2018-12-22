package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @XmlElementWrapper(name = "fileList")
    @XmlElements({@XmlElement(type = MidiCompositionFile.class, name = "midiFile"),
            @XmlElement(type = VideoCompositionFile.class, name = "videoFile"),
            @XmlElement(type = AudioCompositionFile.class, name = "audioFile")})
    @JsonProperty("fileList")
    public List<CompositionFile> getCompositionFileList() {
        return compositionFileList;
    }

    public void setCompositionFileList(List<CompositionFile> compositionFileList) {
        this.compositionFileList = compositionFileList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

}
