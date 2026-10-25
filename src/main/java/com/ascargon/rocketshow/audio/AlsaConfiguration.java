package com.ascargon.rocketshow.audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The configuration of ALSA, e.g. the devices of the audio buses in .asoundrc. ALSA reads it once in a process, unless
 * told to read it again.
 */
public final class AlsaConfiguration {

    private final static Logger logger = LoggerFactory.getLogger(AlsaConfiguration.class);

    private interface AsoundLibrary extends Library {

        int snd_config_update_free_global();

    }

    private AlsaConfiguration() {
    }

    // The next device opened reads the configuration again. Devices already open keep the configuration they were
    // opened with.
    public static void reload() {
        try {
            Native.load("asound", AsoundLibrary.class).snd_config_update_free_global();
        } catch (UnsatisfiedLinkError e) {
            logger.warn("Could not make ALSA read the audio settings again, they apply after a restart", e);
        }
    }

}
