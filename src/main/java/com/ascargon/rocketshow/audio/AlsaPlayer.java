package com.ascargon.rocketshow.audio;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;

import com.ascargon.rocketshow.Settings.AudioOutput;
import com.ascargon.rocketshow.util.ShellManager;

public class AlsaPlayer {

    final static Logger logger = Logger.getLogger(AlsaPlayer.class);

    private ShellManager shellManager;

    private String path;
    private String device;

    public void play(String device, String path) throws IOException {
        shellManager = new ShellManager(new String[]{"aplay", "-D", "plug:" + device, path});
    }

    public void close() throws Exception {
        if (shellManager != null) {
            shellManager.getProcess().destroy();
            shellManager.getProcess().waitFor();
        }
    }

}
