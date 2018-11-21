package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.DefaultCompositionFileService;
import com.ascargon.rocketshow.composition.DefaultCompositionService;
import com.ascargon.rocketshow.dmx.DefaultDmxService;
import com.ascargon.rocketshow.dmx.DefaultMidi2DmxConvertService;
import com.ascargon.rocketshow.image.ImageDisplayer;
import com.ascargon.rocketshow.midi.MidiInDeviceReceiver;
import com.ascargon.rocketshow.raspberry.RaspberryGpioControlActionExecuter;
import com.ascargon.rocketshow.util.DefaultControlActionExecutionService;
import com.ascargon.rocketshow.util.ShellManager;
import com.ascargon.rocketshow.util.Updater;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;

public class Manager {

    private final static Logger logger = LoggerFactory.getLogger(Manager.class);

    private Updater updater;

    private DefaultCompositionService defaultCompositionService;
    private DefaultCompositionFileService defaultCompositionFileService;

    private DefaultControlActionExecutionService defaultControlActionExecutionService;

    private ImageDisplayer imageDisplayer;
    private MidiInDeviceReceiver midiInDeviceReceiver;

    private DefaultDmxService defaultDmxService;
    private DefaultMidi2DmxConvertService defaultMidi2DmxConvertService;

    private Session session;
    private Settings settings;

    // The Raspberry GPIO controller
    private RaspberryGpioControlActionExecuter raspberryGpioControlActionExecuter;

    private boolean isInitializing = true;

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
        //defaultDmxService = new DefaultDmxService();
        defaultMidi2DmxConvertService = new DefaultMidi2DmxConvertService(defaultDmxService);

        // Initialize the image displayer and display a default black screen
        // TODO
//        try {
//            imageDisplayer = new ImageDisplayer();
//            imageDisplayer.display(BASE_PATH + "black.jpg");
//        } catch (IOException e) {
//            logger.error("Could not initialize image displayer", e);
//        }


        // Initialize the required objects inside settings
//        if (settings.getDeviceInMidiRoutingList() != null) {
//            for (MidiRouting deviceInMidiRouting : settings.getDeviceInMidiRoutingList()) {
//                deviceInMidiRouting.load(this);
//            }
//        }

        // Initialize the Raspberry GPIO control action executer
//        try {
//            raspberryGpioControlActionExecuter = new RaspberryGpioControlActionExecuter(this);
//        } catch (Exception e) {
//            logger.error("Could not initialize the Raspberry GPIO controller", e);
//        }

        isInitializing = false;

        logger.info("Finished initializing");
    }

    public void close() {
        logger.info("Close...");

        if (defaultDmxService != null) {
            try {
                defaultDmxService.close();
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

    public DefaultMidi2DmxConvertService getDefaultMidi2DmxConvertService() {
        return defaultMidi2DmxConvertService;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public DefaultDmxService getDefaultDmxService() {
        return defaultDmxService;
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

    public DefaultControlActionExecutionService getDefaultControlActionExecutionService() {
        return defaultControlActionExecutionService;
    }

}
