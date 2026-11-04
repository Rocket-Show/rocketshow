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

    // The library by the name it has on every Linux, which the dynamic linker finds itself and gives the copy already
    // loaded for GStreamer. Only as a fallback the short name: JNA then looks for libasound.so, which only comes with
    // the development package, and else searches directories itself, guessed wrong for ARM and completed by ldconfig.
    private final static String[] LIBRARY_NAMES = {"libasound.so.2", "asound"};

    private interface AsoundLibrary extends Library {

        int snd_config_update_free_global();

    }

    private AlsaConfiguration() {
    }

    // The next device opened reads the configuration again. Devices already open keep the configuration they were
    // opened with.
    public static void reload() {
        UnsatisfiedLinkError error = null;

        for (String libraryName : LIBRARY_NAMES) {
            try {
                Native.load(libraryName, AsoundLibrary.class).snd_config_update_free_global();
                logger.info("ALSA reads the changed audio settings");
                return;
            } catch (UnsatisfiedLinkError e) {
                error = e;
            }
        }

        logger.warn("Could not make ALSA read the audio settings again, they apply after a restart", error);
    }

}
