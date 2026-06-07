package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.settings.SettingsService;
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

    // Regular reboot or tryboot reboot into the other RAUC update slot after
    // installing a new bundle
    public void reboot(boolean tryboot) throws InterruptedException, IOException {
        if (!tryboot) {
            for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
                if (remoteDevice.isSynchronize()) {
                    remoteDevice.reboot();
                }
            }
        }

        ShellManager shellManager;

        if (tryboot) {
            // Tryboot into the other slot
            shellManager = new ShellManager(new String[]{
                    "sudo",
                    "reboot",
                    "0 tryboot"
            });
        } else {
            // Regular reboot
            shellManager = new ShellManager(new String[]{"sudo", "reboot"});
        }

        shellManager.getProcess().waitFor();
    }

    @Override
    public void reboot() throws InterruptedException, IOException {
        reboot(false);
    }

    @Override
    public void tryboot() throws InterruptedException, IOException {
        reboot(true);
    }

}
