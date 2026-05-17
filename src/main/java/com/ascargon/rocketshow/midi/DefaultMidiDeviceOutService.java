package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import purejavacomm.SerialPort;

import jakarta.annotation.PreDestroy;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class DefaultMidiDeviceOutService implements MidiDeviceOutService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiDeviceOutService.class);

    private final SettingsService settingsService;
    private final MidiService midiService;
    private Timer connectMidiDeviceTimer;
    private javax.sound.midi.MidiDevice midiDevice;
    private SerialPort midiSerialDevice;

    public DefaultMidiDeviceOutService(SettingsService settingsService, MidiService midiService) {
        this.settingsService = settingsService;
        this.midiService = midiService;

        // Try to connect to the MIDI out devices
        connectMidiDevices();
    }

    private boolean connectMidiDevice(MidiDevice settingsMidiDevice) {
        // Connect to a real MIDI device
        javax.sound.midi.MidiDevice midiDevice;

        try {
            midiDevice = midiService.getHardwareMidiDevice(settingsMidiDevice, MidiDirection.OUT);

            if (midiDevice == null) {
                logger.trace("MIDI OUT device not found. Try again in 10 seconds.");
            } else {
                midiDevice.open();
                this.midiDevice = midiDevice;
                logger.info("Successfully connected to MIDI OUT device " + this.midiDevice.getDeviceInfo().getName());
                return true;
            }
        } catch (MidiUnavailableException midiUnavailableException) {
            logger.debug("Could not connect to MIDI OUT device", midiUnavailableException);
        }

        return false;
    }

    private boolean connectMidiSerialDevice(MidiDevice settingsMidiDevice) {
        // Connect to a MIDI device, which is a serial port in reality
        SerialPort midiSerialDevice = midiService.getHardwareMidiSerialDevice(settingsMidiDevice, MidiDirection.OUT);

        if (midiSerialDevice == null) {
            logger.trace("MIDI OUT serial device not found.");
            return false;
        }

        this.midiSerialDevice = midiSerialDevice;
        return true;
    }

    // Connect to out devices. Retry, if it failed.
    private void connectMidiDevices() {
        com.ascargon.rocketshow.midi.MidiDevice settingsMidiDevice;
        boolean connected = false;

        // Cancel an eventually existing timer
        if (connectMidiDeviceTimer != null) {
            connectMidiDeviceTimer.cancel();
            connectMidiDeviceTimer = null;
        }

        if (midiDevice != null || midiSerialDevice != null) {
            // Already connected
            return;
        }

        settingsMidiDevice = settingsService.getSettings().getMidiOutDevice();

        if (settingsMidiDevice != null) {
            logger.trace("Try connecting to MIDI OUT device " + settingsMidiDevice.getId() + " \"" + settingsMidiDevice.getName() + "\"");

            if (settingsMidiDevice.isSerialPort()) {
                connected = connectMidiSerialDevice(settingsMidiDevice);
            } else {
                connected = connectMidiDevice(settingsMidiDevice);
            }
        }

        if (connected) {
            // We found a MIDI out device
            if (connectMidiDeviceTimer != null) {
                connectMidiDeviceTimer.cancel();
                connectMidiDeviceTimer = null;
            }
        } else {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    connectMidiDevices();
                }
            };

            connectMidiDeviceTimer = new Timer();
            connectMidiDeviceTimer.schedule(timerTask, 10000);
        }
    }

    @PreDestroy
    private void close() {
        if (midiDevice != null) {
            midiDevice.close();
            midiDevice = null;
        }
    }

    @Override
    public void reconnectMidiDevice() {
        close();
        connectMidiDevices();
    }

    @Override
    public javax.sound.midi.MidiDevice getMidiDevice() {
        return midiDevice;
    }

    @Override
    public SerialPort getMidiSerialDevice() {
        return midiSerialDevice;
    }

    @Override
    public boolean isConnected() {
        return midiDevice != null || midiSerialDevice != null;
    }

    @Override
    public void sendMessage(MidiMessage message) {
        if (!isConnected()) {
            return;
        }

        if (midiDevice != null) {
            try {
                midiDevice.getReceiver().send(message, -1);
            } catch (Exception e) {
                logger.error("Could not send MIDI signal to out device receiver " + midiDevice.getDeviceInfo().getName(), e);
            }
        } else if (midiSerialDevice != null) {
            try {
                byte[] data = message.getMessage();
                OutputStream output = midiSerialDevice.getOutputStream();
                output.write(data);
                output.flush();
            } catch (Exception e) {
                logger.error("Could not send MIDI signal to serial out device receiver", e);
            }
        }
    }

}
