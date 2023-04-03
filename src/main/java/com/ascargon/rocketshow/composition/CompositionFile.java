package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MidiCompositionFile.class, name = "midiFile"),
        @JsonSubTypes.Type(value = AudioCompositionFile.class, name = "audioFile"),
        @JsonSubTypes.Type(value = VideoCompositionFile.class, name = "videoFile")
})
abstract public class CompositionFile {

    public enum CompositionFileType {
        MIDI, AUDIO, VIDEO, UNKNOWN
    }

    private String name;
    private boolean active = true;
    private long durationMillis;
    private boolean loop = false;
    private int offsetMillis = 0;

    protected CompositionFile() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOffsetMillis() {
        return offsetMillis;
    }

    public void setOffsetMillis(int offsetMillis) {
        this.offsetMillis = offsetMillis;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public CompositionFileType getType() {
        return CompositionFileType.UNKNOWN;
    }

}
