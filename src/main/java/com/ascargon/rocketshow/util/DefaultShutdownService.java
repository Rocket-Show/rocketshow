package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.RemoteDevice;
import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.SettingsService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultShutdownService implements ShutdownService {

    private final SettingsService settingsService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    public DefaultShutdownService(SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) {
        this.settingsService = settingsService;
        this.operatingSystemInformationService = operatingSystemInformationService;
    }

    @Override
    public void shutdown() throws InterruptedException, IOException {
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.shutdown();
            }
        }

        if (!OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            // Don't shutdown the system if we're not running on Raspbian
            return;
        }

        ShellManager shellManager = new ShellManager(new String[]{"sudo", "shutdown", "-h", "now"});
        shellManager.getProcess().waitFor();
    }

}
