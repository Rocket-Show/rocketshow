package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

@XmlRootElement
public class MidiMapping {

    private MidiMapping parent;

    private List<ChannelMapping> channelMap = new ArrayList<>();

    private Integer channelOffset = 0;
    private Integer noteOffset = 0;

    // Set at runtime only. Not sent to the web app, where it would be saved into every routing.
    @XmlTransient
    @JsonIgnore
    public MidiMapping getParent() {
        return parent;
    }

    public void setParent(MidiMapping parent) {
        this.parent = parent;
    }

    public List<ChannelMapping> getChannelMap() {
        return channelMap;
    }

    @SuppressWarnings("unused")
    public void setChannelMap(List<ChannelMapping> channelMap) {
        this.channelMap = channelMap;
    }

    public Integer getChannelOffset() {
        return channelOffset;
    }

    public void setChannelOffset(Integer channelOffset) {
        this.channelOffset = channelOffset;
    }

    public Integer getNoteOffset() {
        return noteOffset;
    }

    public void setNoteOffset(Integer noteOffset) {
        this.noteOffset = noteOffset;
    }

}
