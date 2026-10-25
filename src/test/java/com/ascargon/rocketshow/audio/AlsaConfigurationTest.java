package com.ascargon.rocketshow.audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Changed audio settings without a restart: ALSA reads the changed .asoundrc. The devices are of the type null, which
 * opens without a sound card.
 */
@EnabledOnOs(OS.LINUX)
class AlsaConfigurationTest {

    private interface AsoundLibrary extends Library {

        int snd_pcm_open(PointerByReference pcm, String name, int stream, int mode);

        int snd_pcm_close(Pointer pcm);

    }

    private interface CLibrary extends Library {

        int setenv(String name, String value, int overwrite);

    }

    @TempDir
    Path home;

    private AsoundLibrary asound;

    private boolean opens(String device) {
        PointerByReference pcm = new PointerByReference();

        if (asound.snd_pcm_open(pcm, device, 0, 0) != 0) {
            return false;
        }

        asound.snd_pcm_close(pcm.getValue());
        return true;
    }

    private void writeAsoundrc(String device) throws Exception {
        Files.writeString(home.resolve(".asoundrc"), "pcm." + device + " { type null }\n");
    }

    @Test
    void readsTheChangedAudioSettings() throws Exception {
        try {
            asound = Native.load("asound", AsoundLibrary.class);
        } catch (UnsatisfiedLinkError e) {
            assumeTrue(false, "ALSA is not installed");
        }

        // ALSA reads the .asoundrc in the home directory
        CLibrary c = Native.load("c", CLibrary.class);
        String originalHome = System.getenv("HOME");
        c.setenv("HOME", home.toString(), 1);

        try {
            AlsaConfiguration.reload();
            writeAsoundrc("rs_first");
            assertTrue(opens("rs_first"));

            // E.g. another sound card for a bus: ALSA keeps the settings it read
            writeAsoundrc("rs_second");
            assertFalse(opens("rs_second"));

            AlsaConfiguration.reload();
            assertTrue(opens("rs_second"));
            assertFalse(opens("rs_first"));
        } finally {
            c.setenv("HOME", originalHome, 1);
            AlsaConfiguration.reload();
        }
    }

}
