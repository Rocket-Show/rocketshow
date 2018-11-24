package com.ascargon.rocketshow.image;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultImageDisplayingService implements ImageDisplayingService {

    private ShellManager shellManager;

    public DefaultImageDisplayingService(SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) throws IOException {
        shellManager = new ShellManager(new String[]{"sh"});

        // Display a default black screen on Raspbian
        if (OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            display(settingsService.getSettings().getBasePath() + "black.jpg");
        }
    }

    @Override
    public void display(String path) {
        shellManager.sendCommand("sudo fbi -T 1 -a -noverbose " + path, true);
    }

}
