package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/**
 * Handle the MIDI events from the currently connected MIDI input device.
 *
 * @author Moritz A. Vieli
 */
class MidiInDeviceReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(MidiInDeviceReceiver.class);

    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final MidiControlActionExecutionService midiControlActionExecutionService;

    private final MidiRouter midiRouter;

    MidiInDeviceReceiver(ActivityNotificationMidiService activityNotificationMidiService, MidiControlActionExecutionService midiControlActionExecutionService, SettingsService settingsService, MidiRouterFactory midiRouterFactory) {
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;

        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getDeviceInMidiRoutingList());
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        // Process MIDI events as actions according to the settings
        try {
            midiControlActionExecutionService.processMidiSignal(midiMessage);
        } catch (Exception e) {
            logger.error("Could not execute action from MIDI device", e);
        }

        // Process the MIDI events through the defined routings
        try {
            if (midiMessage instanceof ShortMessage) {
                midiRouter.sendSignal(new MidiSignal((ShortMessage) midiMessage), MidiSource.IN_DEVICE);
            }
        } catch (InvalidMidiDataException e) {
            logger.error("Could not route event from MIDI device", e);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }

}
