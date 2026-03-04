package com.ascargon.rocketshow.image;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class DefaultImageDisplayingService implements ImageDisplayingService {

    private ShellManager shellManager;

    public DefaultImageDisplayingService(SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) throws IOException {
        shellManager = new ShellManager(new String[]{"sh"});

        // Display a default black screen on Raspbian
        if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            display(new ApplicationHome(RocketShowApplication.class).getDir().toString() + File.separator + "black.jpg");
        }
    }

    @Override
    public void display(String path) {
        shellManager.sendCommand("sudo fbi -T 1 -a -noverbose " + path, true);
    }

}