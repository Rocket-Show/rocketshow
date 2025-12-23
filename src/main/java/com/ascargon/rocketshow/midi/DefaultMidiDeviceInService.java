package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import purejavacomm.SerialPort;
import purejavacomm.SerialPortEvent;
import purejavacomm.SerialPortEventListener;

import javax.annotation.PreDestroy;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

@Service
public class DefaultMidiDeviceInService implements MidiDeviceInService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiDeviceInService.class);

    private final SettingsService settingsService;
    private final MidiService midiService;
    private final MidiRouter midiRouter;
    private Timer connectMidiDeviceTimer;
    private javax.sound.midi.MidiDevice midiDevice;
    private SerialPort midiSerialDevice;
    private final MidiInDeviceReceiver midiInDeviceReceiver;

    public DefaultMidiDeviceInService(SettingsService settingsService, ActionMidiExecutionService actionMidiExecutionService, MidiService midiService, MidiRouterFactory midiRouterFactory) {
        this.settingsService = settingsService;
        this.midiService = midiService;

        // Initialize the MIDI in device receiver to executeFromTrigger MIDI control actions
        midiInDeviceReceiver = new MidiInDeviceReceiver(actionMidiExecutionService, settingsService, midiRouterFactory);

        // Initialize the MIDI router
        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getDeviceInMidiRoutingList());

        // Try to connect to MIDI in devices
        connectMidiDevices();
    }

    private boolean connectMidiDevice(MidiDevice settingsMidiDevice) {
        // Connect to a real MIDI device
        javax.sound.midi.MidiDevice midiDevice;

        try {
            midiDevice = midiService.getHardwareMidiDevice(settingsMidiDevice, MidiDirection.IN);

            if (midiDevice == null) {
                logger.trace("MIDI IN device not found. Try again in 10 seconds.");
                return false;
            }

            midiDevice.open();

            // Connect the transmitters (getTransmitter() returns a new transmitter each call)
            midiRouter.connectTransmitter(midiDevice.getTransmitter(), midiDevice.getTransmitter());

            midiDevice.getTransmitter().setReceiver(midiInDeviceReceiver);

            logger.info("Successfully connected to MIDI IN device " + midiDevice.getDeviceInfo().getName());
            this.midiDevice = midiDevice;
            return true;
        } catch (MidiUnavailableException midiUnavailableException) {
            logger.debug("Could not connect to MIDI IN device", midiUnavailableException);
        }

        return false;
    }

    private boolean connectMidiSerialDevice(MidiDevice settingsMidiDevice) {
        // Connect to a MIDI device, which is a serial port in reality
        SerialPort midiSerialDevice = midiService.getHardwareMidiSerialDevice(settingsMidiDevice, MidiDirection.IN);

        if (midiSerialDevice == null) {
            logger.trace("MIDI IN serial device not found.");
            return false;
        }

        MidiMessageParser parser = new MidiMessageParser();
        ByteBuffer buffer = ByteBuffer.allocate(256);
        InputStream input = null;

        try {
            input = midiSerialDevice.getInputStream();
        } catch (IOException e) {
            logger.error("Could not open MIDI serial port input stream", e);
        }

        InputStream finalInput = input;

        try {
            midiSerialDevice.addEventListener(new SerialPortEventListener() {
                private final byte[] buffer = new byte[64];

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
                        try {
                            int n;
                            // Read everything that’s currently buffered
                            while ((n = finalInput.read(buffer)) > 0) {
                                for (int i = 0; i < n; i++) {
                                    byte b = buffer[i];
                                    try {
                                        Optional<MidiMessage> maybe = parser.offerByte(b);
                                        maybe.ifPresent(midiMessage -> {
                                            logger.info("Received MIDI message over serial: {}", midiMessage.toString());
                                            midiInDeviceReceiver.send(midiMessage, -1);
                                        });
                                    } catch (InvalidMidiDataException e) {
                                        logger.error("Invalid MIDI data received: {}", e.getMessage());
                                    }
                                }

                                // If nothing more is available, stop this event iteration
                                if (finalInput.available() == 0) {
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            logger.error("Error while reading MIDI data from serial port", e);
                        }
                    }
                }
            });
        } catch (TooManyListenersException e) {
            logger.error("Could not add listener to MIDI serial port", e);
        }

        this.midiSerialDevice = midiSerialDevice;
        return true;
    }

    // Connect to midi in devices. Retry, if it failed.
    private void connectMidiDevices() {
        MidiDevice settingsMidiDevice;
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

        settingsMidiDevice = settingsService.getSettings().getMidiInDevice();

        if (settingsMidiDevice != null) {
            logger.trace("Try connecting to MIDI IN device " + settingsMidiDevice.getId() + " \"" + settingsMidiDevice.getName() + "\"");

            if (settingsMidiDevice.isSerialPort()) {
                connected = connectMidiSerialDevice(settingsMidiDevice);
            } else {
                connected = connectMidiDevice(settingsMidiDevice);
            }
        }

        if (connected) {
            // We connected to a MIDI in device
            if (connectMidiDeviceTimer != null) {
                connectMidiDeviceTimer.cancel();
                connectMidiDeviceTimer = null;
            }
        } else {
            // Connection did not work, try again later
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

        midiRouter.close();
    }

    @Override
    public void reconnectMidiDevice() throws MidiUnavailableException {
        close();
        connectMidiDevices();
    }

    @Override
    public javax.sound.midi.MidiDevice getMidiDevice() {
        return midiDevice;
    }

}
