package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.RemoteDevice;
import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.SettingsService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultRebootService implements RebootService {

    private final SettingsService settingsService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    public DefaultRebootService(SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) {
        this.settingsService = settingsService;
        this.operatingSystemInformationService = operatingSystemInformationService;
    }

    @Override
    public void reboot() throws InterruptedException, IOException {
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.reboot();
            }
        }

        if (!OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            // Restart the app instead of a complete system reboot. Restarting
            // the app should be enough, because all the additional settings
            // (access point, etc.) do not work anyway on systems other than
            // Raspbian.

            Thread restartThread = new Thread(() -> {
                RocketShowApplication.restart();
            });
            restartThread.setDaemon(false);
            restartThread.start();

            return;
        }

        ShellManager shellManager = new ShellManager(new String[]{"sudo", "reboot"});
        shellManager.getProcess().waitFor();
    }

}
