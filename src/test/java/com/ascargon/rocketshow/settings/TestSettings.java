package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.midi.MidiService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The settings service of a unit with its data in a directory of the test, e.g. for the services storing files.
 */
public class TestSettings {

    private TestSettings() {
    }

    public static SettingsService inDirectory(Path directory) {
        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);

        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());

        return new DefaultSettingsService(operatingSystemInformationService, mock(MidiService.class), deviceInformationService, mock(ApplicationEventPublisher.class), directory.toString());
    }

}
