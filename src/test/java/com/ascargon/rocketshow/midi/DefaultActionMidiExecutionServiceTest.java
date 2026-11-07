package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultActionMidiExecutionServiceTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private final ScenePlayerService scenePlayerService = mock(ScenePlayerService.class);
    private final DefaultActionMidiExecutionService actionMidiExecutionService = new DefaultActionMidiExecutionService(settingsService, actionExecutionService, scenePlayerService);

    private final Settings settings = new Settings();

    @BeforeEach
    void setUp() {
        when(settingsService.getSettings()).thenReturn(settings);
    }

    private ActionTriggerMidiNoteOn addNoteOnTrigger(Integer channel, Integer note) {
        ActionTriggerMidiNoteOn actionTriggerMidiNoteOn = new ActionTriggerMidiNoteOn();
        actionTriggerMidiNoteOn.setChannel(channel);
        actionTriggerMidiNoteOn.setNote(note);
        settings.getActionTriggerMidiList().add(actionTriggerMidiNoteOn);
        return actionTriggerMidiNoteOn;
    }

    private ActionTriggerMidiProgramChange addProgramChangeTrigger(Integer channel, Integer program) {
        ActionTriggerMidiProgramChange actionTriggerMidiProgramChange = new ActionTriggerMidiProgramChange();
        actionTriggerMidiProgramChange.setChannel(channel);
        actionTriggerMidiProgramChange.setProgram(program);
        settings.getActionTriggerMidiList().add(actionTriggerMidiProgramChange);
        return actionTriggerMidiProgramChange;
    }

    private ActionTriggerMidiControlChange addControlChangeTrigger(Integer channel, Integer controller, Integer value) {
        ActionTriggerMidiControlChange actionTriggerMidiControlChange = new ActionTriggerMidiControlChange();
        actionTriggerMidiControlChange.setChannel(channel);
        actionTriggerMidiControlChange.setController(controller);
        actionTriggerMidiControlChange.setValue(value);
        settings.getActionTriggerMidiList().add(actionTriggerMidiControlChange);
        return actionTriggerMidiControlChange;
    }

    private ActionTriggerMidiSongSelect addSongSelectTrigger(Integer channel, Integer song) {
        ActionTriggerMidiSongSelect actionTriggerMidiSongSelect = new ActionTriggerMidiSongSelect();
        actionTriggerMidiSongSelect.setChannel(channel);
        actionTriggerMidiSongSelect.setSong(song);
        settings.getActionTriggerMidiList().add(actionTriggerMidiSongSelect);
        return actionTriggerMidiSongSelect;
    }

    private ActionTriggerMidiSystemRealTime addSystemRealTimeTrigger(ActionTriggerMidiSystemRealTime.SystemRealTimeType type) {
        ActionTriggerMidiSystemRealTime actionTriggerMidiSystemRealTime = new ActionTriggerMidiSystemRealTime();
        actionTriggerMidiSystemRealTime.setSystemRealTimeType(type);
        settings.getActionTriggerMidiList().add(actionTriggerMidiSystemRealTime);
        return actionTriggerMidiSystemRealTime;
    }

    private void receive(int command, int channel, int data1, int data2) throws Exception {
        actionMidiExecutionService.processMidiSignal(new ShortMessage(command, channel, data1, data2));
    }

    // A system message carries no channel: its whole status byte is the message
    private void receiveSystem(int status, int data1) throws Exception {
        actionMidiExecutionService.processMidiSignal(new ShortMessage(status, data1, 0));
    }

    private void receiveSystem(int status) throws Exception {
        actionMidiExecutionService.processMidiSignal(new ShortMessage(status));
    }

    @Test
    void triggersOnTheNoteAndChannel() throws Exception {
        ActionTriggerMidiNoteOn trigger = addNoteOnTrigger(1, 60);

        receive(ShortMessage.NOTE_ON, 1, 60, 100);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void ignoresOtherNotesAndChannels() throws Exception {
        addNoteOnTrigger(1, 60);

        receive(ShortMessage.NOTE_ON, 1, 61, 100);
        receive(ShortMessage.NOTE_ON, 2, 60, 100);

        verify(actionExecutionService, never()).executeFromTrigger(any());
    }

    @Test
    void triggersOnEveryNoteAndChannelIfNotSet() throws Exception {
        ActionTriggerMidiNoteOn trigger = addNoteOnTrigger(null, null);

        receive(ShortMessage.NOTE_ON, 9, 36, 127);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void ignoresReleasedKeys() throws Exception {
        // Many keyboards send a released key as note on with velocity 0, often with running status
        addNoteOnTrigger(1, 60);

        receive(ShortMessage.NOTE_ON, 1, 60, 0);
        receive(ShortMessage.NOTE_OFF, 1, 60, 64);

        verify(actionExecutionService, never()).executeFromTrigger(any());
    }

    @Test
    void triggersOnTheProgramChange() throws Exception {
        ActionTriggerMidiProgramChange trigger = addProgramChangeTrigger(null, 5);

        receive(ShortMessage.PROGRAM_CHANGE, 3, 5, 0);
        receive(ShortMessage.PROGRAM_CHANGE, 3, 6, 0);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void keepsNotesAndProgramChangesApart() throws Exception {
        addNoteOnTrigger(null, 5);
        addProgramChangeTrigger(null, 60);

        receive(ShortMessage.PROGRAM_CHANGE, 0, 5, 0);
        receive(ShortMessage.NOTE_ON, 0, 60, 100);
        receive(ShortMessage.CONTROL_CHANGE, 0, 5, 127);

        verify(actionExecutionService, never()).executeFromTrigger(any());
    }

    @Test
    void executesEveryMatchingTrigger() throws Exception {
        ActionTriggerMidiNoteOn trigger = addNoteOnTrigger(1, 60);
        ActionTriggerMidiNoteOn anyNoteTrigger = addNoteOnTrigger(null, null);

        receive(ShortMessage.NOTE_ON, 1, 60, 100);

        verify(actionExecutionService).executeFromTrigger(trigger);
        verify(actionExecutionService).executeFromTrigger(anyNoteTrigger);
    }


    @Test
    void triggersOnTheControllerAndItsValue() throws Exception {
        ActionTriggerMidiControlChange trigger = addControlChangeTrigger(null, 64, 127);

        receive(ShortMessage.CONTROL_CHANGE, 0, 64, 127);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void triggersOnEveryValueOfTheControllerIfNotSet() throws Exception {
        ActionTriggerMidiControlChange trigger = addControlChangeTrigger(null, 64, null);

        receive(ShortMessage.CONTROL_CHANGE, 0, 64, 3);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void ignoresAnotherValueOfTheController() throws Exception {
        addControlChangeTrigger(null, 64, 127);

        receive(ShortMessage.CONTROL_CHANGE, 0, 64, 0);
        receive(ShortMessage.CONTROL_CHANGE, 0, 65, 127);

        verify(actionExecutionService, never()).executeFromTrigger(any());
    }

    @Test
    void triggersOnTheSongSelect() throws Exception {
        ActionTriggerMidiSongSelect trigger = addSongSelectTrigger(null, 5);

        receiveSystem(0xF3, 5);
        receiveSystem(0xF3, 6);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    @Test
    void triggersOnTheTransportMessage() throws Exception {
        ActionTriggerMidiSystemRealTime trigger = addSystemRealTimeTrigger(ActionTriggerMidiSystemRealTime.SystemRealTimeType.CONTINUE);

        receiveSystem(0xFB);
        receiveSystem(0xFA);
        receiveSystem(0xFC);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    /**
     * A system message has no channel: the low nibble of 0xF3 is part of the status byte, but
     * ShortMessage#getChannel() still reports it as channel 3.
     */
    @Test
    void theChannelOfATriggerDoesNotApplyToSystemMessages() throws Exception {
        ActionTriggerMidiSongSelect trigger = addSongSelectTrigger(0, 5);

        receiveSystem(0xF3, 5);

        verify(actionExecutionService).executeFromTrigger(trigger);
    }


    @Test
    void endsTheFlashOfATriggerWhenItsKeyOrButtonIsLetGo() throws Exception {
        ActionTriggerMidiNoteOn note = addNoteOnTrigger(0, 36);
        ActionTriggerMidiControlChange button = addControlChangeTrigger(0, 20, 127);
        ActionTriggerMidiControlChange knob = addControlChangeTrigger(0, 21, null);

        receive(ShortMessage.NOTE_OFF, 0, 36, 64);
        receive(ShortMessage.NOTE_ON, 0, 36, 0);
        verify(actionExecutionService, org.mockito.Mockito.times(2)).releaseFromTrigger(note);

        receive(ShortMessage.CONTROL_CHANGE, 0, 20, 0);
        verify(actionExecutionService).releaseFromTrigger(button);

        // a control without a value of its own isn't let go: 0 is one of its values
        receive(ShortMessage.CONTROL_CHANGE, 0, 21, 0);
        verify(actionExecutionService, org.mockito.Mockito.never()).releaseFromTrigger(knob);

        // another note or channel isn't the key
        receive(ShortMessage.NOTE_OFF, 1, 36, 0);
        receive(ShortMessage.NOTE_OFF, 0, 37, 0);
        verify(actionExecutionService, org.mockito.Mockito.times(2)).releaseFromTrigger(note);
    }

    @Test
    void letsASceneWaitingForItsKeyTakeTheMessageAlone() throws Exception {
        ActionTriggerMidiNoteOn note = addNoteOnTrigger(0, 36);
        when(scenePlayerService.learnMidi(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        receive(ShortMessage.NOTE_ON, 0, 36, 100);

        verify(actionExecutionService, org.mockito.Mockito.never()).executeFromTrigger(note);
    }

    @Test
    void givesTheScenesTheTempoOfAMidiClock() throws Exception {
        // a clock tick triggers nothing
        ActionTriggerMidiNoteOn anyNote = addNoteOnTrigger(null, null);

        for (int i = 0; i < 24 * 2 + 1; i++) {
            actionMidiExecutionService.processMidiSignal(new ShortMessage(0xF8));
        }

        // once a beat, from the second one on
        verify(scenePlayerService, org.mockito.Mockito.times(2)).setClockTempo(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.eq(false));
        verify(actionExecutionService, org.mockito.Mockito.never()).executeFromTrigger(anyNote);
    }
}
