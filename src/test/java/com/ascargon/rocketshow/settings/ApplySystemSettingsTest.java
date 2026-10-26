package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.DefaultAudioService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.util.LanInfo;
import com.ascargon.rocketshow.util.LanService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Applying the settings to the system of a Raspberry Pi on each save: the commands and files are recorded, not run.
 */
class ApplySystemSettingsTest {

    private final Settings settings = new Settings();
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final List<String> files = new CopyOnWriteArrayList<>();
    private final LanInfo lanInfo = new LanInfo();
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
    private final AtomicInteger alsaReloads = new AtomicInteger();
    private DefaultSettingsUpdateSystemService settingsUpdateSystemService;

    @BeforeEach
    void setUp() {
        settings.setWlanApEnable(true);
        settings.setWlanApSsid("Rocket Show");
        settings.setWlanApPassphrase("long-enough-passphrase");
        settings.setWlanApCountryCode("CH");
        settings.setTlsEnable(false);
        settings.setLanStaticIpEnable(false);
        settings.setLoggingLevel(Settings.LoggingLevel.INFO);
        settings.setAudioOutput(Settings.AudioOutput.HEADPHONES);
        lanInfo.setIpAddress("192.168.1.20");

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);
        LanService lanService = mock(LanService.class);
        when(lanService.getLanInfo()).thenReturn(lanInfo);

        settingsUpdateSystemService = new DefaultSettingsUpdateSystemService(settingsService, new DefaultAudioService(operatingSystemInformationService),
                operatingSystemInformationService, mock(LightingService.class), lanService, applicationEventPublisher) {
            @Override
            void runCommand(String... command) {
                commands.add(String.join(" ", command).replaceAll("/\\S*/(set-[a-z-]+\\.sh)", "$1"));
            }

            @Override
            void writeFile(String path, String content) {
                files.add(path);
            }

            @Override
            void reloadAlsaConfiguration() {
                alsaReloads.incrementAndGet();
            }
        };
    }

    private List<String> apply() throws Exception {
        commands.clear();
        files.clear();
        settingsUpdateSystemService.update();
        settingsUpdateSystemService.waitForCommands();
        return List.copyOf(commands);
    }

    @Test
    void appliesEverythingAtTheStart() throws Exception {
        List<String> commands = apply();

        assertEquals(List.of(
                "sudo set-wifi-ap-country.sh CH",
                "sudo systemctl enable hostapd",
                "sudo systemctl restart hostapd",
                "sudo set-lan-ip.sh dhcp",
                "sudo sed -i s/^ip[[:space:]]*=[[:space:]]*.*/ip = 192.168.1.20/ /etc/ola/ola-artnet.conf",
                "sudo set-connection-mode.sh http"
        ), commands);
        assertEquals(List.of("/etc/hostapd/hostapd.conf"), files);
    }

    @Test
    void keepsTheNetworkWhenOtherSettingsAreSaved() throws Exception {
        apply();

        // E.g. a MIDI routing changed during a show, while the web app is connected through the access point
        settings.setOffsetMillisMidi(10);

        assertEquals(List.of(), apply());
    }

    @Test
    void restartsTheAccessPointWhenItChanged() throws Exception {
        apply();

        settings.setWlanApPassphrase("another-long-passphrase");

        assertEquals(List.of(
                "sudo set-wifi-ap-country.sh CH",
                "sudo systemctl enable hostapd",
                "sudo systemctl restart hostapd"
        ), apply());

        settings.setWlanApEnable(false);
        assertEquals(List.of("sudo systemctl disable hostapd", "sudo systemctl stop hostapd"), apply().subList(1, 3));
    }

    @Test
    void appliesAChangedLanAndConnectionMode() throws Exception {
        apply();

        settings.setLanStaticIpEnable(true);
        settings.setLanIpAddress("10.0.0.5");
        settings.setLanGateway("10.0.0.1");
        settings.setTlsEnable(true);
        lanInfo.setIpAddress("10.0.0.5");

        List<String> commands = apply();
        assertTrue(commands.contains("sudo set-lan-ip.sh static 10.0.0.5 255.255.255.0 10.0.0.1  "), commands.toString());
        assertTrue(commands.contains("sudo set-connection-mode.sh https"), commands.toString());
        assertTrue(commands.stream().anyMatch(command -> command.contains("ip = 10.0.0.5")), commands.toString());
    }

    @Test
    void opensTheAudioDevicesAgainWhenTheyChanged() throws Exception {
        settings.setAudioOutput(Settings.AudioOutput.DEVICE);
        AudioBus audioBus = new AudioBus();
        audioBus.setName("Main");
        audioBus.setChannels(2);
        audioBus.setAudioDevice(audioDevice(1, "Headphones"));
        settings.getAudioBusList().add(audioBus);

        // At the start, the devices are opened after
        apply();
        assertEquals(List.of(System.getProperty("user.home") + File.separator + ".asoundrc"), files.subList(0, 1));

        settings.setOffsetMillisMidi(10);
        apply();
        assertEquals(List.of(), files);

        // Another sound card for the bus
        audioBus.setAudioDevice(audioDevice(2, "UMC1820"));
        apply();

        assertEquals(List.of(System.getProperty("user.home") + File.separator + ".asoundrc"), files);
        assertEquals(1, alsaReloads.get());
        verify(applicationEventPublisher, times(1)).publishEvent(any(AudioSettingsChangedEvent.class));
    }

    private static AudioDevice audioDevice(int id, String key) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey(key);
        audioDevice.setName(key + " name");
        return audioDevice;
    }

}
