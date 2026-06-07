package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

/**
 * A MIDI device containing name and id.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
public class MidiDevice {

    private int id;
    private String name;
    private String vendor;
    private String description;
    private boolean serialPort = false;

}
