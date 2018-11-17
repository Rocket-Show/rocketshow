package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.Settings;
import com.ascargon.rocketshow.SettingsService;

/**
 * Resets all USB interfaces. This is sometimes needed for the raspberry pi to
 * properly connect to USB devices after booting.
 *
 * @author Moritz A. Vieli
 */
public class ResetUsb {

    private SettingsService settingsService;

    public ResetUsb(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void resetAllInterfaces() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("lsusb");
        Process process = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        // Loop over each interface
        while ((line = br.readLine()) != null) {
            // Extract the bus and the device from each line
            String bus = line.substring(4, 7);
            String device = line.substring(15, 18);
            String name = line.substring(33);

            if (!name.startsWith("Standard Microsystems Corp") && !name.startsWith("Linux Foundation 2.0")) {
                // Reset the interface
                ShellManager shellManager = new ShellManager(new String[]{"sudo", settingsService.getSettings().getBasePath() + "bin/usbreset",
                        "/dev/bus/usb/" + bus + "/" + device});

                shellManager.getProcess().waitFor();
            }
        }
    }

}
