package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.DefaultAudioService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.util.LanService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The configuration files the settings are written to: .asoundrc for the audio devices and hostapd.conf for the
 * wireless access point. A broken file silences a device or locks the user out of the access point.
 */
class SystemConfigFilesTest {

    private final Settings settings = new Settings();
    private DefaultSettingsUpdateSystemService settingsUpdateSystemService;

    private static AudioDevice audioDevice(int id, String key) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey(key);
        audioDevice.setName(key + " name");
        return audioDevice;
    }

    private void addAudioBus(String name, AudioDevice audioDevice, int channels) {
        AudioBus audioBus = new AudioBus();
        audioBus.setName(name);
        audioBus.setAudioDevice(audioDevice);
        audioBus.setChannels(channels);
        settings.getAudioBusList().add(audioBus);
    }

    @BeforeEach
    void setUp() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        settingsUpdateSystemService = new DefaultSettingsUpdateSystemService(settingsService, new DefaultAudioService(operatingSystemInformationService),
                operatingSystemInformationService, mock(LightingService.class), mock(LanService.class), mock(ApplicationEventPublisher.class));

        settings.setAudioOutput(Settings.AudioOutput.DEVICE);
        settings.setAlsaPeriodSize(16384);
        settings.setAlsaBufferSize(5);

        settings.setWlanApSsid("Rocket Show");
        settings.setWlanApHwMode("g");
        settings.setWlanApChannel(7);
    }

    @Test
    void writesNoAudioDevicesWithoutBuses() {
        assertEquals("", settingsUpdateSystemService.getAlsaSettings(settings));
    }

    @Test
    void mixesAllBusesOfADevice() {
        AudioDevice usbInterface = audioDevice(2, "UMC1820");
        AudioDevice headphones = audioDevice(1, "Device");
        addAudioBus("Click", usbInterface, 2);
        addAudioBus("Main", headphones, 2);
        addAudioBus("Backing", usbInterface, 4);
        addAudioBus("Unused", null, 2);

        assertEquals("""
                pcm.dmix_Device {
                  type dmix
                  ipc_key 104401
                  slave {
                    pcm "hw:Device"
                    channels 2
                    period_size 16384
                    buffer_size 81920
                  }
                  bindings {
                    0 0
                    1 1
                  }
                }

                pcm.rs_Device {
                  type plug
                  slave {
                    pcm "dmix_Device"
                    channels 2
                  }
                  ttable.0.0 1
                  ttable.1.1 1
                }
                pcm.dmix_UMC1820 {
                  type dmix
                  ipc_key 104411
                  slave {
                    pcm "hw:UMC1820"
                    channels 6
                    period_size 16384
                    buffer_size 81920
                  }
                  bindings {
                    0 0
                    1 1
                    2 2
                    3 3
                    4 4
                    5 5
                  }
                }

                pcm.rs_UMC1820 {
                  type plug
                  slave {
                    pcm "dmix_UMC1820"
                    channels 6
                  }
                  ttable.0.0 1
                  ttable.1.1 1
                  ttable.2.2 1
                  ttable.3.3 1
                  ttable.4.4 1
                  ttable.5.5 1
                }
                """, settingsUpdateSystemService.getAlsaSettings(settings));
    }

    @Test
    void namesDevicesWithSpecialCharacters() {
        addAudioBus("Main", audioDevice(3, "My Device-2"), 2);
        settings.setAlsaPeriodTime(5000);

        String alsaSettings = settingsUpdateSystemService.getAlsaSettings(settings);

        assertEquals(true, alsaSettings.startsWith("""
                pcm.dmix_My_Device2 {
                  type dmix
                  ipc_key 104401
                  slave {
                    pcm "hw:My Device-2"
                    channels 2
                    period_time 5000
                    period_size 16384
                """), alsaSettings);
        assertEquals(true, alsaSettings.contains("pcm.rs_My_Device2 {"), alsaSettings);
    }

    @Test
    void protectsTheAccessPointWithThePassphrase() {
        settings.setWlanApPassphrase("secret-passphrase");
        settings.setWlanApCountryCode("CH");

        assertEquals("""
                interface=wlan0
                driver=nl80211
                ssid=Rocket Show
                utf8_ssid=1
                hw_mode=g
                channel=7
                country_code=CH
                wmm_enabled=0
                macaddr_acl=0
                auth_algs=1
                ignore_broadcast_ssid=0
                wpa=2
                wpa_passphrase=secret-passphrase
                wpa_key_mgmt=WPA-PSK
                wpa_pairwise=TKIP
                rsn_pairwise=CCMP
                """, settingsUpdateSystemService.getHostapdConfig(settings));
    }

    @Test
    void opensAHiddenAccessPointWithoutPassphrase() {
        settings.setWlanApPassphrase("");
        settings.setWlanApSsidHide(true);

        assertEquals("""
                interface=wlan0
                driver=nl80211
                ssid=Rocket Show
                utf8_ssid=1
                hw_mode=g
                channel=7
                wmm_enabled=0
                macaddr_acl=0
                auth_algs=1
                ignore_broadcast_ssid=1
                wpa_key_mgmt=WPA-PSK
                wpa_pairwise=TKIP
                rsn_pairwise=CCMP
                """, settingsUpdateSystemService.getHostapdConfig(settings));
    }

}
