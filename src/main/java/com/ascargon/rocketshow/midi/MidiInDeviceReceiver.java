package com.ascargon.rocketshow.midi;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;

/**
 * Handle the MIDI events from the currently connected MIDI input device.
 *
 * @author Moritz A. Vieli
 */
public class MidiInDeviceReceiver implements Receiver {

    private final static Logger logger = Logger.getLogger(MidiInDeviceReceiver.class);

    private NotificationService notificationService;
    private SettingsService settingsService;
    private MidiControlActionExecutionService midiControlActionExecutionService;

    private Timer connectTimer;
    private javax.sound.midi.MidiDevice midiReceiver;
    private List<MidiRouting> midiRoutingList;
    private boolean midiLearn = false;

    public MidiInDeviceReceiver(NotificationService notificationService, SettingsService settingsService, MidiControlActionExecutionService midiControlActionExecutionService) {
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;

        midiRoutingList = settingsService.getSettings().getDeviceInMidiRoutingList();

        try {
            connectMidiReceiver();
        } catch (MidiUnavailableException e) {
            logger.error("Could not initialize the MIDI receiver", e);
        }
    }

    /**
     * Connect the MIDI player to a sender, if required. Also call this method,
     * if you change the settings or want to reconnect the device.
     */
    public void connectMidiReceiver() throws MidiUnavailableException {
        // Cancel an eventually existing timer
        if (connectTimer != null) {
            connectTimer.cancel();
            connectTimer = null;
        }

        if (midiReceiver != null && midiReceiver.isOpen()) {
            // We already have an open receiver -> close this one
            try {
                midiReceiver.close();
            } catch (Exception e) {
                logger.error("Could not close MIDI receiver", e);
            }
        }

        MidiDevice midiDevice = settingsService.getSettings().getMidiInDevice();

        logger.trace("Try connecting to input MIDI device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");

        midiReceiver = MidiUtil.getHardwareMidiDevice(midiDevice, MidiDirection.IN);

        if (midiReceiver == null) {
            logger.trace("MIDI input device not found. Try again in 5 seconds.");

            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        connectMidiReceiver();
                    } catch (Exception e) {
                        logger.debug("Could not connect to MIDI input device", e);
                    }
                }
            };

            connectTimer = new Timer();
            connectTimer.schedule(timerTask, 5000);

            return;
        }

        // We found the device
        if (connectTimer != null) {
            connectTimer.cancel();
            connectTimer = null;
        }

        midiReceiver.open();

        // Set the MIDI routing receivers
        for (MidiRouting midiRouting : midiRoutingList) {
            midiRouting.setTransmitter(midiReceiver.getTransmitter());
        }

        // Also set this class as a second receiver to execute the MIDI actions
        // (midiReceiver.getTransmitter returns a different transmitter each
        // time)
        midiReceiver.getTransmitter().setReceiver(this);

        logger.info("Successfully connected to input MIDI device " + midiDevice.getId() + " \"" + midiDevice.getName()
                + "\"");
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);

        // Notify the frontend, if midi learn is activated
        if (midiLearn) {
            try {
                notificationService.notifyClients(midiSignal);
            } catch (Exception e) {
                logger.error("Could not notify the clients about a MIDI learn event", e);
            }
        }

        // Process MIDI events as actions according to the settings
        try {
            midiControlActionExecutionService.processMidiSignal(midiSignal);
        } catch (Exception e) {
            logger.error("Could not execute action from live MIDI", e);
        }
    }

    @Override
    public void close() {
        if (connectTimer != null) {
            connectTimer.cancel();
        }

        if (midiReceiver != null && midiReceiver.isOpen()) {
            midiReceiver.close();
        }
    }

    public void setMidiLearn(boolean midiLearn) {
        this.midiLearn = midiLearn;
    }

}
