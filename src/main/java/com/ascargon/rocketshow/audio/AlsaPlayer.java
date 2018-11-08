package com.ascargon.rocketshow.audio;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.util.ShellManager;

public class AlsaPlayer {

    private final static Logger logger = Logger.getLogger(AlsaPlayer.class);

    private ShellManager shellManager;

    public void play(String device, String path) throws IOException {
        logger.debug("Play " + path + " on ALSA device " + device);
        shellManager = new ShellManager(new String[]{"aplay", "-D", "plug:" + device, path});
    }

    public void close() throws Exception {
        if (shellManager != null) {
            shellManager.getProcess().destroy();
            shellManager.getProcess().waitFor();
        }
    }

}
