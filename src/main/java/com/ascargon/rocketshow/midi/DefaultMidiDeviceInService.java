package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class DefaultMidiDeviceInService implements MidiDeviceInService {

    private final static Logger logger = LogManager.getLogger(DefaultMidiDeviceInService.class);

    private SettingsService settingsService;
    private MidiRoutingService midiRoutingService;

    private Timer connectMidiDeviceTimer;

    private javax.sound.midi.MidiDevice midiInDevice;

    private MidiInDeviceReceiver midiInDeviceReceiver;

    public DefaultMidiDeviceInService(SettingsService settingsService, NotificationService notificationService, MidiControlActionExecutionService midiControlActionExecutionService, MidiRoutingService midiRoutingService) {
        this.settingsService = settingsService;
        this.midiRoutingService = midiRoutingService;

        // Initialize the MIDI in device receiver to execute MIDI control actions
        midiInDeviceReceiver = new MidiInDeviceReceiver(notificationService, midiControlActionExecutionService);

        // Try to connect to MIDI in/out devices
        try {
            connectMidiDevices();
        } catch (MidiUnavailableException e) {
            logger.error("Could not initialize the MIDI in device", e);
        }
    }

    // Connect to midi in and out devices. Retry, if it failed.
    private void connectMidiDevices() throws MidiUnavailableException {
        MidiDevice midiDevice;

        // Cancel an eventually existing timer
        if (connectMidiDeviceTimer != null) {
            connectMidiDeviceTimer.cancel();
            connectMidiDeviceTimer = null;
        }

        if (midiInDevice == null) {
            midiDevice = settingsService.getSettings().getMidiInDevice();

            logger.trace(
                    "Try connecting to MIDI in device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");

            midiInDevice = MidiUtil.getHardwareMidiDevice(midiDevice, MidiUtil.MidiDirection.IN);

            if (midiInDevice == null) {
                logger.trace("MIDI in device not found. Try again in 10 seconds.");
            } else {
                midiInDevice.open();

                midiRoutingService.connectTransmitter(midiInDevice.getTransmitter(), settingsService.getSettings().getDeviceInMidiRoutingList());

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

    @Override
    public void reconnectMidiDevice() throws MidiUnavailableException {
        if (midiInDevice != null) {
            midiInDevice.close();
            midiInDevice = null;
        }

        connectMidiDevices();
    }

    @Override
    public javax.sound.midi.MidiDevice getMidiInDevice() {
        return midiInDevice;
    }

}
