package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MidiCompositionFile.class, name = "midiFile"),
        @JsonSubTypes.Type(value = AudioCompositionFile.class, name = "audioFile"),
        @JsonSubTypes.Type(value = VideoCompositionFile.class, name = "videoFile")
})
@Getter
@Setter
public abstract class CompositionFile {

    public enum CompositionFileType {
        MIDI, AUDIO, VIDEO
    }

    private String name;
    private boolean active = true;
    private long durationMillis;
    private boolean loop = false;
    private int offsetMillis = 0;

    public abstract CompositionFileType getType();

}
