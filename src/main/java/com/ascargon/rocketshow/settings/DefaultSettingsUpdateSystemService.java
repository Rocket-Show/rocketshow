package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Update the system according to the specified settings.
 */
@Service
public class DefaultSettingsUpdateSystemService implements SettingsUpdateSystemService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSettingsUpdateSystemService.class);

    private final SettingsService settingsService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final AudioService audioService;
    private final LightingService lightingService;

    public DefaultSettingsUpdateSystemService(
            SettingsService settingsService, AudioService audioService,
            OperatingSystemInformationService operatingSystemInformationService,
            LightingService lightingService
    ) {
        this.settingsService = settingsService;
        this.audioService = audioService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.lightingService = lightingService;
    }

    private void setSystemAudioOutput(int id) throws Exception {
        // TODO Not supported currently
        //ShellManager shellManager = new ShellManager(new String[]{"amixer", "cset", "numid=3", String.valueOf(id)});
        //shellManager.getProcess().waitFor();
    }

    private String getAlsaSettings(Settings settings) {
        // Generate the ALSA settings
        StringBuilder alsaSettings = new StringBuilder();

        List<AudioDevice> audioDeviceList = audioService.getAudioDeviceInUseList(settings);

        if (audioDeviceList.isEmpty()) {
            // We got no audio device
            return "";
        }

        // Build the dmix device

        // Just a random number above 100'000
        int ipcKey = 104401;

        for (AudioDevice audioDevice : audioDeviceList) {
            int currentChannel = 0;

            String audioDeviceAlsaName = audioService.getAudioDeviceAlsaName(audioDevice);
            int channelCount = audioService.getChannelCountByAudioDevice(settings, audioDevice);

            alsaSettings.append("pcm.dmix_").append(audioDeviceAlsaName).
                    append(" {\n").
                    append("  type dmix\n").
                    append("  ipc_key ").append(ipcKey).append("\n").
                    append("  slave {\n").
                    append("    pcm \"hw:").append(audioDevice.getKey()).append("\"\n")
                    .append("    channels ").append(channelCount).append("\n");

            if (settings.getAlsaPeriodTime() != null) {
                alsaSettings.append("    period_time ").append(settings.getAlsaPeriodTime()).append("\n");
            }

            if (settings.getAlsaPeriodSize() != null) {
                alsaSettings.append("    period_size ").append(settings.getAlsaPeriodSize()).append("\n");
            }

            if (settings.getAlsaBufferSize() != null && settings.getAlsaPeriodSize() != null) {
                alsaSettings.append("    buffer_size ").append(settings.getAlsaBufferSize() * settings.getAlsaPeriodSize()).append("\n");
            }

            alsaSettings.append("  }\n")
                    .append("  bindings {\n");

            // Add all channels
            for (int i = 0; i < channelCount; i++) {
                alsaSettings.append("    ").append(i).append(" ").append(i).append("\n");
            }

            alsaSettings.append("""
                      }
                    }
                    """);

            // Create the rocketshow device
            alsaSettings.append("\n" + "pcm.rs_").append(audioDeviceAlsaName).append(" {\n").
                    append("  type plug\n").
                    append("  slave {\n").
                    append("    pcm \"dmix_").append(audioDeviceAlsaName).append("\"\n").
                    append("    channels ").append(channelCount).append("\n").
                    append("  }\n");

            // Add each channel to the bus
            for (int j = 0; j < channelCount; j++) {
                alsaSettings.append("  ttable.").append(j).append(".").append(currentChannel).append(" 1\n");
                currentChannel++;
            }

            alsaSettings.append("}\n");

            // Ensure uniqueness of the key
            ipcKey += 10;
        }

        return alsaSettings.toString();
    }

    private void updateAudioSystem(Settings settings) throws Exception {
        // not supported currently
        if (settings.getAudioOutput() == Settings.AudioOutput.HEADPHONES && OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            setSystemAudioOutput(1);
        } else if (settings.getAudioOutput() == Settings.AudioOutput.HDMI && OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            setSystemAudioOutput(2);
        } else if (settings.getAudioOutput() == Settings.AudioOutput.DEVICE) {
            // Write the audio settings to /home/.asoundrc and use ALSA to
            // output audio on the selected device name
            logger.debug("Write ALSA settings");

            String alsaSettingsPath = System.getProperty("user.home") + File.separator + ".asoundrc";
            if (settingsService.isReadOnlyFileSystem()) {
                alsaSettingsPath = "/data/rocketshow/.asoundrc";
            }

            // Create a new file the Rocket Show settings
            try {
                FileWriter fileWriter = new FileWriter(alsaSettingsPath, false);
                fileWriter.write(getAlsaSettings(settings));
                fileWriter.close();
            } catch (IOException e) {
                logger.error("Could not write .asoundrc", e);
            }
        }
    }

    private void updateLoggingLevel(Settings settings) {
        // Set the proper logging level (map from the log4j enum to our own
        // enum)

        String loggerName = "com.ascargon.rocketshow";

        switch (settings.getLoggingLevel()) {
            case INFO:
                Configurator.setLevel(loggerName, Level.INFO);
                break;
            case WARN:
                Configurator.setLevel(loggerName, Level.WARN);
                break;
            case ERROR:
                Configurator.setLevel(loggerName, Level.ERROR);
                break;
            case DEBUG:
                Configurator.setLevel(loggerName, Level.DEBUG);
                break;
            case TRACE:
                Configurator.setLevel(loggerName, Level.TRACE);
                break;
        }
    }

    private void updateWlanAp(Settings settings) {
        String apConfig = "";
        String statusCommand;

        // Update the access point configuration
        apConfig += "interface=wlan0\n";
        apConfig += "driver=nl80211\n";
        apConfig += "ssid=" + settings.getWlanApSsid() + "\n";
        apConfig += "utf8_ssid=1\n";
        apConfig += "hw_mode=" + settings.getWlanApHwMode() + "\n";
        apConfig += "channel=" + settings.getWlanApChannel() + "\n";
        apConfig += "country_code=" + settings.getWlanApCountryCode() + "\n";
        apConfig += "wmm_enabled=0\n";
        apConfig += "macaddr_acl=0\n";
        apConfig += "auth_algs=1\n";

        if (settings.isWlanApSsidHide()) {
            apConfig += "ignore_broadcast_ssid=1\n";
        } else {
            apConfig += "ignore_broadcast_ssid=0\n";
        }

        if (settings.getWlanApPassphrase() != null && settings.getWlanApPassphrase().length() >= 8) {
            apConfig += "wpa=2\n";
            apConfig += "wpa_passphrase=" + settings.getWlanApPassphrase() + "\n";
        }

        apConfig += "wpa_key_mgmt=WPA-PSK\n";
        apConfig += "wpa_pairwise=TKIP\n";
        apConfig += "rsn_pairwise=CCMP\n";

        try {
            String fileName = "/etc/hostapd/hostapd.conf";
            if (settingsService.isReadOnlyFileSystem()) {
                fileName = "/data/rocketshow/hostapd.conf";
            }
            FileWriter fileWriter = new FileWriter(fileName, false);
            fileWriter.write(apConfig);
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Could not write /etc/hostapd/hostapd.conf", e);
        }

        // Activate/deactivate the access point completely
        if (settings.getWlanApEnable()) {
            statusCommand = "enable";
        } else {
            statusCommand = "disable";
        }

        try {
            new ShellManager(new String[]{"sudo", "systemctl", statusCommand, "hostapd"});
        } catch (IOException e) {
            logger.error("Could not update the access point status with '" + statusCommand + "'", e);
        }
    }

    @Override
    public void update() {
        // Update all system settings

        Settings settings = settingsService.getSettings();

        if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            try {
                updateAudioSystem(settings);
            } catch (Exception e) {
                logger.error("Could not update the audio system settings", e);
            }
        }

        try {
            updateLoggingLevel(settings);
        } catch (Exception e) {
            logger.error("Could not update the logging level system settings", e);
        }

        if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            try {
                updateWlanAp(settings);
            } catch (Exception e) {
                logger.error("Could not update the wireless access point settings", e);
            }
        }

        try {
            lightingService.enablePlugins(settings.getLightingOlaPluginList());
        } catch (Exception e) {
            logger.error("Could not activate the OLA plugin", e);
        }
    }

}