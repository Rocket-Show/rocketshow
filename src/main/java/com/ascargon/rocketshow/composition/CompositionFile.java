package com.ascargon.rocketshow.composition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
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

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public int getOffsetMillis() {
        return offsetMillis;
    }

    public void setOffsetMillis(int offsetMillis) {
        this.offsetMillis = offsetMillis;
    }

    @XmlElement
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @XmlElement
    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    @XmlElement
    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    @XmlElement
    public CompositionFileType getType() {
        return CompositionFileType.UNKNOWN;
    }

}
