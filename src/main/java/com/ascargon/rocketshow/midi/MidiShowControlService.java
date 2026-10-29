package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiMessage;

/**
 * Follow the two SysEx-based control protocols a show is usually driven with:
 * <ul>
 *     <li><b>MIDI Show Control (MSC)</b>, spoken by lighting desks and show control systems (ETC,
 *     grandMA, QLab, Medialon). It addresses cues by number, so "load cue 12.5" and "go" select and
 *     start a composition.</li>
 *     <li><b>MIDI Machine Control (MMC)</b>, spoken by DAWs and tape machines. It only carries
 *     transport commands (play, stop, pause, locate), not which composition to play.</li>
 * </ul>
 */
public interface MidiShowControlService {

    /**
     * Offer a message received from the MIDI input device. Ignored unless the matching protocol is
     * switched on and the message is addressed to this device.
     */
    void processMidiMessage(MidiMessage midiMessage);

}
