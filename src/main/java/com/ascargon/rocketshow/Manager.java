package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.DefaultCompositionFileService;
import com.ascargon.rocketshow.composition.DefaultCompositionService;
import com.ascargon.rocketshow.dmx.DmxManager;
import com.ascargon.rocketshow.dmx.Midi2DmxConverter;
import com.ascargon.rocketshow.image.ImageDisplayer;
import com.ascargon.rocketshow.midi.MidiDeviceConnectedListener;
import com.ascargon.rocketshow.midi.MidiInDeviceReceiver;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;
import com.ascargon.rocketshow.raspberry.RaspberryGpioControlActionExecuter;
import com.ascargon.rocketshow.util.ControlActionExecuter;
import com.ascargon.rocketshow.util.ShellManager;
import com.ascargon.rocketshow.util.Updater;
import org.apache.log4j.Logger;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Manager {

    private final static Logger logger = Logger.getLogger(Manager.class);

    private Updater updater;

    private DefaultCompositionService defaultCompositionService;
    private DefaultCompositionFileService defaultCompositionFileService;

    private ControlActionExecuter controlActionExecuter;

    private ImageDisplayer imageDisplayer;
    private MidiInDeviceReceiver midiInDeviceReceiver;

    private DmxManager dmxManager;
    private Midi2DmxConverter midi2DmxConverter;

    private MidiDevice midiOutDevice;
    private Timer connectMidiOutDeviceTimer;
    private List<MidiDeviceConnectedListener> midiOutDeviceConnectedListeners = new ArrayList<>();

    private Session session;
    private Settings settings;

    // The Raspberry GPIO controller
    private RaspberryGpioControlActionExecuter raspberryGpioControlActionExecuter;

    private boolean isInitializing = true;

    public void addMidiOutDeviceConnectedListener(MidiDeviceConnectedListener listener) {
        midiOutDeviceConnectedListeners.add(listener);

        // We already have a device connected -> fire the listener
        if (midiOutDevice != null) {
            listener.deviceConnected(midiOutDevice);
        }
    }

    public void removeMidiOutDeviceConnectedListener(MidiDeviceConnectedListener listener) {
        midiOutDeviceConnectedListeners.remove(listener);
    }

    public void reconnectMidiDevices() throws MidiUnavailableException {
        if (midiInDeviceReceiver != null) {
            midiInDeviceReceiver.connectMidiReceiver();
        }

        connectMidiSender();
    }

    /**
     * Connect to the MIDI out device. Also call this method, if you change the
     * settings or want to reconnect the device.
     */
    private void connectMidiSender() throws MidiUnavailableException {
        // Cancel an eventually existing timer
        if (connectMidiOutDeviceTimer != null) {
            connectMidiOutDeviceTimer.cancel();
            connectMidiOutDeviceTimer = null;
        }

        if (midiOutDevice != null && midiOutDevice.isOpen()) {
            // We already have an open sender -> close this one
            try {
                midiOutDevice.close();
            } catch (Exception e) {
                logger.error("Could not close MIDI out device", e);
            }
        }

        com.ascargon.rocketshow.midi.MidiDevice midiDevice = settings.getMidiOutDevice();

        logger.trace(
                "Try connecting to output MIDI device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");

        midiOutDevice = MidiUtil.getHardwareMidiDevice(midiDevice, MidiDirection.OUT);

        if (midiOutDevice == null) {
            logger.trace("MIDI output device not found. Try again in 10 seconds.");

            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        connectMidiSender();
                    } catch (Exception e) {
                        logger.debug("Could not connect to MIDI output device", e);
                    }
                }
            };

            connectMidiOutDeviceTimer = new Timer();
            connectMidiOutDeviceTimer.schedule(timerTask, 10000);

            return;
        }

        // We found a device
        if (connectMidiOutDeviceTimer != null) {
            connectMidiOutDeviceTimer.cancel();
            connectMidiOutDeviceTimer = null;
        }

        midiOutDevice.open();

        // Connect all listeners
        for (MidiDeviceConnectedListener listener : midiOutDeviceConnectedListeners) {
            listener.deviceConnected(midiOutDevice);
        }

        // We found the device and all listeners are connected
        logger.info("Successfully connected to output MIDI device " + midiOutDevice.getDeviceInfo().getName());
    }

    public void load() {
        // Setup iptables, because it's not working properly in pi-gen distro
        // generation
        try {
            new ShellManager(new String[]{"sudo", "iptables", "-t", "nat", "-A", "POSTROUTING", "-o", "eth0", "-j",
                    "MASQUERADE"});
        } catch (IOException e) {
            logger.error("Could not initialize iptables", e);
        }

        // Initialize the DMX manager
        dmxManager = new DmxManager(this);
        midi2DmxConverter = new Midi2DmxConverter(dmxManager);

        // Initialize the image displayer and display a default black screen
        // TODO
//        try {
//            imageDisplayer = new ImageDisplayer();
//            imageDisplayer.display(BASE_PATH + "black.jpg");
//        } catch (IOException e) {
//            logger.error("Could not initialize image displayer", e);
//        }


        // Initialize the required objects inside settings
        if (settings.getDeviceInMidiRoutingList() != null) {
            for (MidiRouting deviceInMidiRouting : settings.getDeviceInMidiRoutingList()) {
                deviceInMidiRouting.load(this);
            }
        }

        // Initialize the MIDI out device
        try {
            connectMidiSender();
        } catch (MidiUnavailableException e) {
            logger.error("Could not initialize the MIDI out device", e);
        }

        // Initialize the Raspberry GPIO control action executer
        try {
            raspberryGpioControlActionExecuter = new RaspberryGpioControlActionExecuter(this);
        } catch (Exception e) {
            logger.error("Could not initialize the Raspberry GPIO controller", e);
        }

        isInitializing = false;

        logger.info("Finished initializing");
    }

    public void close() {
        logger.info("Close...");

        if (dmxManager != null) {
            try {
                dmxManager.close();
            } catch (Exception e) {
                logger.error("Could not close the DMX manager", e);
            }
        }

        // TODO Close all websocket sessions

//        try {
//            // TODO Call after close
//            //player.close();
//        } catch (Exception e) {
//            logger.error("Could not close the player", e);
//        }
//
//        if (connectMidiOutDeviceTimer != null) {
//            connectMidiOutDeviceTimer.cancel();
//        }
//
//        if (midiInDeviceReceiver != null) {
//            try {
//                midiInDeviceReceiver.close();
//            } catch (Exception e) {
//                logger.error("Could not close MIDI in device receiver", e);
//            }
//        }
//
//        if (midiOutDevice != null && midiOutDevice.isOpen()) {
//            try {
//                midiOutDevice.close();
//            } catch (Exception e) {
//                logger.error("Could not close MIDI out device", e);
//            }
//        }
//
//        if (currentCompositionSet != null) {
//            try {
//                currentCompositionSet.close();
//            } catch (Exception e) {
//                logger.error("Could not close current set list", e);
//            }
//        }
//
//        try {
//            stopDefaultComposition();
//        } catch (Exception e) {
//            logger.error("Could not stop the default composition", e);
//        }
//
//        try {
//            raspberryGpioControlActionExecuter.close();
//        } catch (Exception e) {
//            logger.error("Could not close the Raspberry GPIO controller", e);
//        }

        logger.info("Finished closing");
    }

    public void reboot() throws Exception {
        for (RemoteDevice remoteDevice : settings.getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.reboot();
            }
        }

        ShellManager shellManager = new ShellManager(new String[]{"sudo", "reboot"});
        shellManager.getProcess().waitFor();
    }

    public Midi2DmxConverter getMidi2DmxConverter() {
        return midi2DmxConverter;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public DmxManager getDmxManager() {
        return dmxManager;
    }

    public DefaultCompositionService getDefaultCompositionService() {
        return defaultCompositionService;
    }

    public Updater getUpdater() {
        return updater;
    }

    public Session getSession() {
        return session;
    }

    public DefaultCompositionFileService getDefaultCompositionFileService() {
        return defaultCompositionFileService;
    }

    public void setDefaultCompositionFileService(DefaultCompositionFileService defaultCompositionFileService) {
        this.defaultCompositionFileService = defaultCompositionFileService;
    }

    public MidiInDeviceReceiver getMidiInDeviceReceiver() {
        return midiInDeviceReceiver;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public ControlActionExecuter getControlActionExecuter() {
        return controlActionExecuter;
    }

}
