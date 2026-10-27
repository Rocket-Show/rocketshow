package com.ascargon.rocketshow.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The displays connected to the HDMI outputs, as the kernel tells (/sys/class/drm), on simulated graphics cards.
 */
class DefaultHdmiServiceTest {

    @TempDir
    Path drm;

    // The EDID of a display with its name in a descriptor
    private static byte[] edid(String name) {
        byte[] edid = new byte[128];
        int descriptor = 72;
        edid[descriptor + 3] = (byte) 0xFC;
        byte[] text = (name + "\n            ").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, edid, descriptor + 5, 13);
        return edid;
    }

    private void connector(String name, String status, String modes, byte[] edid) throws Exception {
        Path connector = Files.createDirectories(drm.resolve(name));
        Files.writeString(connector.resolve("status"), status + "\n");
        Files.writeString(connector.resolve("modes"), modes);
        Files.write(connector.resolve("edid"), edid);
    }

    @Test
    void findsADisplayOnTheSecondHdmiOutput() throws Exception {
        // A Raspberry Pi 4 or 5
        connector("card1-HDMI-A-1", "disconnected", "", new byte[0]);
        connector("card1-HDMI-A-2", "connected", "1920x1200\n1920x1080\n1280x720\n", edid("DELL U2415"));
        Files.createDirectories(drm.resolve("card1-Composite-1"));
        DefaultHdmiService hdmiService = new DefaultHdmiService(drm);

        // Videos play on it
        assertTrue(hdmiService.isConnected());
        assertTrue(hdmiService.isDisplayConnected());
        assertEquals(List.of(new HdmiService.ConnectedDisplay("HDMI-A-2", "1920x1200", "DELL U2415")), hdmiService.getConnectedDisplays());
    }

    @Test
    void findsADisplayOnAnotherGraphicsCard() throws Exception {
        connector("card0-HDMI-A-1", "connected", "1280x720\n", new byte[0]);

        assertEquals(List.of(new HdmiService.ConnectedDisplay("HDMI-A-1", "1280x720", null)), new DefaultHdmiService(drm).getConnectedDisplays());
    }

    @Test
    void noDisplayConnected() throws Exception {
        connector("card1-HDMI-A-1", "disconnected", "", new byte[0]);
        DefaultHdmiService hdmiService = new DefaultHdmiService(drm);

        assertFalse(hdmiService.isConnected());
        assertFalse(hdmiService.isDisplayConnected());
        assertEquals(List.of(), hdmiService.getConnectedDisplays());
    }

    @Test
    void videosPlayWhenItCannotBeTold() {
        DefaultHdmiService hdmiService = new DefaultHdmiService(drm.resolve("missing"));

        assertTrue(hdmiService.isConnected());
        assertFalse(hdmiService.isDisplayConnected());
    }

    @Test
    void readsTheNameOfTheDisplayFromItsEdid() {
        assertEquals("DELL U2415", DefaultHdmiService.getEdidName(edid("DELL U2415")));
        assertEquals("LG TV SSCR2", DefaultHdmiService.getEdidName(edid("LG TV SSCR2")));
        assertNull(DefaultHdmiService.getEdidName(new byte[128]));
        assertNull(DefaultHdmiService.getEdidName(new byte[10]));
    }

}
