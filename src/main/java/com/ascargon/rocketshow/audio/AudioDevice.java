package com.ascargon.rocketshow.audio;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@XmlRootElement
@Getter
@Setter
public class AudioDevice {

    private int id;
    private String key;
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioDevice that = (AudioDevice) o;
        return id == that.id &&
                Objects.equals(key, that.key) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, name);
    }

}
