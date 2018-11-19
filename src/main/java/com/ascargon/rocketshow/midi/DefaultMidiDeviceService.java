package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.dmx.Midi2DmxConvertService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class DefaultMidiDeviceService implements MidiDeviceService {

    private final static Logger logger = LogManager.getLogger(DefaultMidiDeviceService.class);

    private SettingsService settingsService;
    private Midi2DmxConvertService midi2DmxConvertService;
    private DmxService dmxService;

    private Timer connectMidiDevicesTimer;

    private javax.sound.midi.MidiDevice midiOutDevice;
    private javax.sound.midi.MidiDevice midiInDevice;

    private List<MidiRoutingManager> midiDeviceInRoutingManagerList = new ArrayList<>();

    private MidiInDeviceReceiver midiInDeviceReceiver;

    public DefaultMidiDeviceService(SettingsService settingsService, NotificationService notificationService, MidiControlActionExecutionService midiControlActionExecutionService, Midi2DmxConvertService midi2DmxConvertService, DmxService dmxService) {
        this.settingsService = settingsService;
        this.midi2DmxConvertService = midi2DmxConvertService;
        this.dmxService = dmxService;

        // Initialize the MIDI in device receiver to execute MIDI control actions
        midiInDeviceReceiver = new MidiInDeviceReceiver(notificationService, midiControlActionExecutionService);

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
        if (connectMidiDevicesTimer != null) {
            connectMidiDevicesTimer.cancel();
            connectMidiDevicesTimer = null;
        }

        if (midiOutDevice == null) {
            midiDevice = settingsService.getSettings().getMidiOutDevice();

            logger.trace(
                    "Try connecting to MIDI out device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");

            midiOutDevice = MidiUtil.getHardwareMidiDevice(midiDevice, MidiUtil.MidiDirection.OUT);

            if (midiOutDevice == null) {
                logger.trace("MIDI out device not found. Try again in 10 seconds.");
            } else {
                midiOutDevice.open();
                logger.info("Successfully connected to MIDI out device " + midiOutDevice.getDeviceInfo().getName());
            }
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

                midiDeviceInRoutingManagerList = new ArrayList<>();

                for (MidiRouting midiRouting : settingsService.getSettings().getDeviceInMidiRoutingList()) {
                    MidiRoutingManager midiRoutingManager = new MidiRoutingManager(settingsService, midi2DmxConvertService, dmxService, this, midiInDevice.getTransmitter(), midiRouting);
                    this.midiDeviceInRoutingManagerList.add(midiRoutingManager);
                }

                midiInDevice.getTransmitter().setReceiver(midiInDeviceReceiver);

                logger.info("Successfully connected to MIDI in device " + midiInDevice.getDeviceInfo().getName());
            }
        }

        if (midiOutDevice == null || midiInDevice == null) {
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

            connectMidiDevicesTimer = new Timer();
            connectMidiDevicesTimer.schedule(timerTask, 10000);
        } else {
            // We found both, a MIDI in and a MIDI out device
            if (connectMidiDevicesTimer != null) {
                connectMidiDevicesTimer.cancel();
                connectMidiDevicesTimer = null;
            }
        }

    }

    @Override
    public void reconnectMidiDevices() throws MidiUnavailableException {
        midiDeviceInRoutingManagerList = new ArrayList<>();

        if (midiOutDevice != null) {
            midiOutDevice.close();
            midiOutDevice = null;
        }

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

    @Override
    public javax.sound.midi.MidiDevice getMidiOutDevice() {
        return midiOutDevice;
    }

}
