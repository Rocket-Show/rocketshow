package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.composition.SetComposition;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMidiCompositionSelectionServiceTest {

    private final static long VERIFY_TIMEOUT_MILLIS = 2_000;

    private Settings settings;
    private CompositionService compositionService;
    private SetService setService;
    private PlayerService playerService;
    private DefaultMidiCompositionSelectionService service;

    private final List<Composition> compositions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        settings = new Settings();
        settings.setMidiCompositionSelectionEnabled(true);
        settings.setMidiCompositionSelectionAutoPlay(false);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        compositionService = mock(CompositionService.class);
        when(compositionService.getAllCompositions()).thenReturn(compositions);
        when(compositionService.getComposition(any())).thenAnswer(invocation -> compositions.stream()
                .filter(composition -> composition.getName().equals(invocation.getArgument(0)))
                .findFirst()
                .orElse(null));

        setService = mock(SetService.class);
        playerService = mock(PlayerService.class);

        service = new DefaultMidiCompositionSelectionService(settingsService, compositionService, setService, playerService);
    }

    private Composition composition(String name, Integer midiNumber, String showControlCue) {
        Composition composition = new Composition();
        composition.setName(name);
        composition.setMidiNumber(midiNumber);
        composition.setShowControlCue(showControlCue);
        compositions.add(composition);
        return composition;
    }

    private void loadSet(SetComposition... setCompositions) {
        Set set = new Set();
        set.getSetCompositionList().addAll(List.of(setCompositions));
        when(setService.getCurrentSet()).thenReturn(set);
    }

    private SetComposition setComposition(String name, Integer midiNumber, String showControlCue) {
        SetComposition setComposition = new SetComposition();
        setComposition.setName(name);
        setComposition.setMidiNumber(midiNumber);
        setComposition.setShowControlCue(showControlCue);
        return setComposition;
    }

    private void verifySelected(String compositionName) throws Exception {
        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS))
                .setComposition(eq(compositions.stream().filter(c -> c.getName().equals(compositionName)).findFirst().orElseThrow()), anyBoolean(), anyBoolean());
    }

    @Test
    void selectsTheCompositionCarryingTheMidiNumber() throws Exception {
        composition("Intro", 1, null);
        composition("Song 2", 2, null);

        assertTrue(service.selectByMidiNumber(2, false));

        verifySelected("Song 2");
        verify(playerService, never()).play();
    }

    @Test
    void reportsAnUnknownMidiNumber() throws Exception {
        composition("Intro", 1, null);

        assertFalse(service.selectByMidiNumber(7, false));

        verify(playerService, never()).setComposition(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void playsTheCompositionWhenAsked() throws Exception {
        composition("Intro", 1, null);

        assertTrue(service.selectByMidiNumber(1, true));

        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).play();
    }

    @Test
    void theCurrentSetRenumbersItsCompositions() throws Exception {
        composition("Intro", 50, null);
        composition("Song 2", 51, null);
        loadSet(setComposition("Song 2", 1, null), setComposition("Intro", 2, null));

        assertTrue(service.selectByMidiNumber(1, false));

        verifySelected("Song 2");
        // The set position has to follow, or next/previous jump somewhere else
        verify(setService, timeout(VERIFY_TIMEOUT_MILLIS)).setCurrentCompositionIndex(0);
    }

    @Test
    void aSetCompositionWithoutItsOwnNumberKeepsTheCompositionsNumber() throws Exception {
        composition("Intro", 50, null);
        loadSet(setComposition("Intro", null, null));

        assertTrue(service.selectByMidiNumber(50, false));

        verifySelected("Intro");
    }

    @Test
    void aProgramChangeSelectsTheMatchingNumberCountedFromOne() throws Exception {
        composition("Intro", 1, null);

        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
        service.processMidiMessage(programChange);

        verifySelected("Intro");
    }

    @Test
    void aBankSelectExtendsTheProgramRange() throws Exception {
        composition("Late in the list", 200, null);

        // Bank 1, program 72 -> 1 * 128 + 71 + 1
        ShortMessage bankSelectMsb = new ShortMessage();
        bankSelectMsb.setMessage(ShortMessage.CONTROL_CHANGE, 0, 0, 0);
        ShortMessage bankSelectLsb = new ShortMessage();
        bankSelectLsb.setMessage(ShortMessage.CONTROL_CHANGE, 0, 32, 1);
        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 71, 0);

        service.processMidiMessage(bankSelectMsb);
        service.processMidiMessage(bankSelectLsb);
        service.processMidiMessage(programChange);

        verifySelected("Late in the list");
    }

    @Test
    void aSongSelectSelectsTheMatchingNumber() throws Exception {
        composition("Song 3", 3, null);

        ShortMessage songSelect = new ShortMessage();
        songSelect.setMessage(0xF3, 2, 0);
        service.processMidiMessage(songSelect);

        verifySelected("Song 3");
    }

    @Test
    void ignoresProgramChangesOnOtherChannels() throws Exception {
        composition("Intro", 1, null);
        settings.setMidiCompositionSelectionChannel(3);

        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
        service.processMidiMessage(programChange);

        verify(playerService, never()).setComposition(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void ignoresEverythingWhileSwitchedOff() throws Exception {
        composition("Intro", 1, null);
        settings.setMidiCompositionSelectionEnabled(false);

        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
        service.processMidiMessage(programChange);

        verify(playerService, never()).setComposition(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void selectsByShowControlCue() throws Exception {
        composition("Intro", 1, "12.5");

        assertTrue(service.selectByShowControlCue("12.5", false));

        verifySelected("Intro");
    }

    @Test
    void matchesCueNumbersThatAreWrittenDifferently() throws Exception {
        composition("Intro", 1, "12");

        // Consoles write the same cue as 12, 012 or 12.0
        assertTrue(service.selectByShowControlCue("012", false));

        verifySelected("Intro");
    }

    @Test
    void fallsBackToTheMidiNumberForCompositionsWithoutACueNumber() throws Exception {
        composition("Intro", 4, null);

        assertTrue(service.selectByShowControlCue("4", false));

        verifySelected("Intro");
    }

    @Test
    void reportsAnUnknownCueNumber() throws Exception {
        composition("Intro", 1, "12.5");

        assertFalse(service.selectByShowControlCue("99", false));

        verify(playerService, never()).setComposition(any(), anyBoolean(), anyBoolean());
    }

}
