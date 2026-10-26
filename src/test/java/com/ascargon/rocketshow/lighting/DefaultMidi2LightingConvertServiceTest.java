package com.ascargon.rocketshow.lighting;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MIDI notes to DMX: the note is the channel, the velocity the value.
 */
class DefaultMidi2LightingConvertServiceTest {

    private final LightingService lightingService = mock(LightingService.class);
    private final DefaultMidi2LightingConvertService convertService = new DefaultMidi2LightingConvertService(lightingService);
    private final LightingUniverseState universe = new LightingUniverseState();
    private final Midi2LightingMapping mapping = new Midi2LightingMapping();

    private void process(int command, int note, int velocity) throws Exception {
        convertService.processMidiEvent(new ShortMessage(command, 0, note, velocity), mapping, universe);
    }

    @Test
    void setsTheChannelOfTheNoteToTheVelocity() throws Exception {
        process(ShortMessage.NOTE_ON, 0, 64);
        process(ShortMessage.NOTE_ON, 10, 1);
        process(ShortMessage.NOTE_ON, 127, 126);

        assertEquals(128, universe.getUniverse().get(0));
        assertEquals(2, universe.getUniverse().get(10));
        assertEquals(252, universe.getUniverse().get(127));
        verify(lightingService, times(3)).send();
    }

    @Test
    void reachesFullWithTheHighestVelocity() throws Exception {
        process(ShortMessage.NOTE_ON, 5, 127);

        assertEquals(255, universe.getUniverse().get(5));
    }

    @Test
    void turnsTheChannelOffWithTheNote() throws Exception {
        process(ShortMessage.NOTE_ON, 5, 100);
        process(ShortMessage.NOTE_OFF, 5, 64);
        assertEquals(0, universe.getUniverse().get(5));

        // Many devices send a note on with velocity 0 instead of a note off
        process(ShortMessage.NOTE_ON, 5, 100);
        process(ShortMessage.NOTE_ON, 5, 0);
        assertEquals(0, universe.getUniverse().get(5));
    }

    @Test
    void ignoresOtherMessages() throws Exception {
        process(ShortMessage.CONTROL_CHANGE, 5, 100);
        process(ShortMessage.PROGRAM_CHANGE, 5, 0);

        assertTrue(universe.getUniverse().isEmpty());
        verify(lightingService, never()).send();
    }

}
