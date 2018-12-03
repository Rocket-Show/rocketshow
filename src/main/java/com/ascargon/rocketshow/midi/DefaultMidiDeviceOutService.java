package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sound.midi.MidiUnavailableException;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class DefaultMidiDeviceOutService implements MidiDeviceOutService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiDeviceOutService.class);

    private final SettingsService settingsService;
    private final MidiService midiService;

    private Timer connectMidiDeviceTimer;

    private javax.sound.midi.MidiDevice midiOutDevice;

    public DefaultMidiDeviceOutService(SettingsService settingsService, MidiService midiService) {
        this.settingsService = settingsService;
        this.midiService = midiService;

        // Try to connect to MIDI in/out devices
        try {
            connectMidiDevices();
        } catch (MidiUnavailableException e) {
            logger.error("Could not initialize the MIDI out device", e);
        }
    }

    // Connect to midi in and out devices. Retry, if it failed.
    private void connectMidiDevices() throws MidiUnavailableException {
        com.ascargon.rocketshow.midi.MidiDevice midiDevice;

        // Cancel an eventually existing timer
        if (connectMidiDeviceTimer != null) {
            connectMidiDeviceTimer.cancel();
            connectMidiDeviceTimer = null;
        }

        if (midiOutDevice == null) {
            midiDevice = settingsService.getSettings().getMidiOutDevice();

            if(midiDevice != null) {
                logger.trace(
                        "Try connecting to MIDI out device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");
            }

            midiOutDevice = midiService.getHardwareMidiDevice(midiDevice, DefaultMidiService.MidiDirection.OUT);

            if (midiOutDevice == null) {
                logger.trace("MIDI out device not found. Try again in 10 seconds.");
            } else {
                midiOutDevice.open();

                logger.info("Successfully connected to MIDI out device " + midiOutDevice.getDeviceInfo().getName());
            }
        }

        if (midiOutDevice == null) {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        connectMidiDevices();
                    } catch (Exception e) {
                        logger.debug("Could not connect to MIDI out device", e);
                    }
                }
            };

            connectMidiDeviceTimer = new Timer();
            connectMidiDeviceTimer.schedule(timerTask, 10000);
        } else {
            // We found a MIDI out device
            if (connectMidiDeviceTimer != null) {
                connectMidiDeviceTimer.cancel();
                connectMidiDeviceTimer = null;
            }
        }

    }

    @PreDestroy
    private void close() {
        if (midiOutDevice != null) {
            midiOutDevice.close();
            midiOutDevice = null;
        }
    }

    @Override
    public void reconnectMidiDevice() throws MidiUnavailableException {
        close();
        connectMidiDevices();
    }

    @Override
    public javax.sound.midi.MidiDevice getMidiOutDevice() {
        return midiOutDevice;
    }

}
