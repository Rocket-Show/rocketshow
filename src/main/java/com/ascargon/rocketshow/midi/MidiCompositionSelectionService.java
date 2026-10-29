package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiMessage;

/**
 * Select compositions from incoming MIDI, by the MIDI number configured on them rather than by a
 * mapping table: a master announces which composition it wants ("now open composition 5") and this
 * device finds and selects it.
 * <p>
 * Program change (extended by bank select) and song select are handled here;
 * {@link MidiShowControlService} uses the same lookup for MIDI Show Control cues.
 */
public interface MidiCompositionSelectionService {

    /**
     * Offer a message received from the MIDI input device. Ignored unless selecting compositions by
     * MIDI number is switched on.
     */
    void processMidiMessage(MidiMessage midiMessage);

    /**
     * Select the composition carrying the given MIDI number.
     *
     * @param play true to also start playing it
     * @return false if no composition carries that number
     */
    boolean selectByMidiNumber(int midiNumber, boolean play);

    /**
     * Select the composition carrying the given MIDI Show Control cue number.
     *
     * @param play true to also start playing it
     * @return false if no composition carries that cue number
     */
    boolean selectByShowControlCue(String cue, boolean play);

}
