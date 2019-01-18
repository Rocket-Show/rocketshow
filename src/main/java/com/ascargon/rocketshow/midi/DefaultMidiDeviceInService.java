package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sound.midi.MidiUnavailableException;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class DefaultMidiDeviceInService implements MidiDeviceInService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiDeviceInService.class);

    private final SettingsService settingsService;
    private final MidiService midiService;

    private MidiRouter midiRouter;

    private Timer connectMidiDeviceTimer;

    private javax.sound.midi.MidiDevice midiInDevice;

    private final MidiInDeviceReceiver midiInDeviceReceiver;

    public DefaultMidiDeviceInService(SettingsService settingsService, ActivityNotificationMidiService activityNotificationMidiService, MidiControlActionExecutionService midiControlActionExecutionService, MidiService midiService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService) {
        this.settingsService = settingsService;
        this.midiService = midiService;

        // Initialize the MIDI in device receiver to execute MIDI control actions
        midiInDeviceReceiver = new MidiInDeviceReceiver(activityNotificationMidiService, midiControlActionExecutionService, settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService);

        // Initialize the MIDI router
        midiRouter = new MidiRouter(settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService, activityNotificationMidiService, settingsService.getSettings().getDeviceInMidiRoutingList());

        // Try to connect to MIDI in devices
        try {
            connectMidiDevices();
        } catch (MidiUnavailableException e) {
            logger.error("Could not initialize the MIDI in device", e);
        }
    }

    // Connect to midi in devices. Retry, if it failed.
    private void connectMidiDevices() throws MidiUnavailableException {
        MidiDevice midiDevice;

        // Cancel an eventually existing timer
        if (connectMidiDeviceTimer != null) {
            connectMidiDeviceTimer.cancel();
            connectMidiDeviceTimer = null;
        }

        if (midiInDevice == null) {
            midiDevice = settingsService.getSettings().getMidiInDevice();

            if (midiDevice != null) {
                logger.trace(
                        "Try connecting to MIDI in device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");
            }

            midiInDevice = midiService.getHardwareMidiDevice(midiDevice, MidiSignal.MidiDirection.IN);

            if (midiInDevice == null) {
                logger.trace("MIDI in device not found. Try again in 10 seconds.");
            } else {
                midiInDevice.open();

                // Connect the transmitters (getTransmitter() returns a new transmitter each call)
                midiRouter.connectTransmitter(midiInDevice.getTransmitter(), midiInDevice.getTransmitter());

                midiInDevice.getTransmitter().setReceiver(midiInDeviceReceiver);

                logger.info("Successfully connected to MIDI in device " + midiInDevice.getDeviceInfo().getName());
            }
        }

        if (midiInDevice == null) {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        connectMidiDevices();
                    } catch (Exception e) {
                        logger.debug("Could not connect to MIDI in device", e);
                    }
                }
            };

            connectMidiDeviceTimer = new Timer();
            connectMidiDeviceTimer.schedule(timerTask, 10000);
        } else {
            // We found a MIDI in device
            if (connectMidiDeviceTimer != null) {
                connectMidiDeviceTimer.cancel();
                connectMidiDeviceTimer = null;
            }
        }

    }

    @PreDestroy
    private void close() {
        if (midiInDevice != null) {
            midiInDevice.close();
            midiInDevice = null;
        }

        midiRouter.close();
    }

    @Override
    public void reconnectMidiDevice() throws MidiUnavailableException {
        close();
        connectMidiDevices();
    }

    @Override
    public javax.sound.midi.MidiDevice getMidiInDevice() {
        return midiInDevice;
    }

}
