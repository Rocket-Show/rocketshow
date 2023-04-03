package com.ascargon.rocketshow.midi;


import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * A MIDI device containing name and id.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class MidiDevice {

    private int id;

    private String name;

    private String vendor;

    private String description;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
