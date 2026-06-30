package com.ascargon.rocketshow.lighting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultMidi2LightingConvertServiceTest {

    private LightingService lightingService;
    private DefaultMidi2LightingConvertService convertService;
    private Midi2LightingMapping mapping;
    private LightingUniverseState universeState;

    @BeforeEach
    void setUp() {
        lightingService = mock(LightingService.class);
        convertService = new DefaultMidi2LightingConvertService(lightingService);
        // The default mapping type is SIMPLE
        mapping = new Midi2LightingMapping();
        universeState = new LightingUniverseState();
    }

    @Test
    void noteOnMapsVelocityTimesTwoToTheChannel() throws Exception {
        ShortMessage message = new ShortMessage(ShortMessage.NOTE_ON, 0, 10, 100);

        convertService.processMidiEvent(message, mapping, universeState);

        assertEquals(200, universeState.getUniverse().get(10));
        verify(lightingService).send();
    }

    @Test
    void noteOnExtendsAlmostMaxValueToFullMax() throws Exception {
        // Velocity 127 -> 254, which is extended to the full 255
        ShortMessage message = new ShortMessage(ShortMessage.NOTE_ON, 0, 5, 127);

        convertService.processMidiEvent(message, mapping, universeState);

        assertEquals(255, universeState.getUniverse().get(5));
    }

    @Test
    void noteOffResetsTheChannelToZero() throws Exception {
        universeState.getUniverse().put(10, 200);

        ShortMessage message = new ShortMessage(ShortMessage.NOTE_OFF, 0, 10, 0);
        convertService.processMidiEvent(message, mapping, universeState);

        assertEquals(0, universeState.getUniverse().get(10));
        verify(lightingService).send();
    }

    @Test
    void nonNoteMessagesAreIgnored() throws Exception {
        ShortMessage message = new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 10, 100);

        convertService.processMidiEvent(message, mapping, universeState);

        assertTrue(universeState.getUniverse().isEmpty());
        verify(lightingService, never()).send();
    }
}
