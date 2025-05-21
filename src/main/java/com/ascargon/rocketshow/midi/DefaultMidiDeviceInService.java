package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

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

        new Thread(() -> {
            byte[] temp = new byte[64];
            while (true) {
                int numRead = midiSerialDevice.readBytes(temp, temp.length);
                if (numRead > 0) {
                    buffer.clear();
                    buffer.put(temp, 0, numRead);
                    buffer.flip();

                    try {
                        while (buffer.hasRemaining()) {
                            Optional<MidiMessage> maybeMessage = parser.offerByte(buffer.get());
                            maybeMessage.ifPresent(midiMessage -> {
                                logger.trace("Received MIDI message over serial: " + midiMessage);
                                midiInDeviceReceiver.send(midiMessage, -1);
                            });
                        }
                    } catch (InvalidMidiDataException e) {
                        logger.error("Invalid MIDI data received on MIDI serial device: " + e.getMessage());
                    }
                }

                try {
                    Thread.sleep(1); // Prevent tight loop
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

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
