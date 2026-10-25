package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.util.LanService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final LanService lanService;

    // Runs the system commands one after the other, in the order they were given, without holding up saving the
    // settings
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "system-settings");
        thread.setDaemon(true);
        return thread;
    });

    // The settings applied to the system last. Each save of the settings applies them, but applying them again
    // would e.g. restart the access point and disconnect the web app connected through it, maybe during a show.
    private String appliedWlanAp;
    private String appliedLanIp;
    private String appliedOlaArtNetIp;
    private String appliedConnectionMode;

    public DefaultSettingsUpdateSystemService(
            SettingsService settingsService, AudioService audioService,
            OperatingSystemInformationService operatingSystemInformationService,
            LightingService lightingService,
            LanService lanService
    ) {
        this.settingsService = settingsService;
        this.audioService = audioService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.lightingService = lightingService;
        this.lanService = lanService;
    }

    // Package visible for the tests
    void runCommand(String... command) throws Exception {
        ShellManager shellManager = new ShellManager(command, false);
        String output = new String(shellManager.getProcess().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = shellManager.getProcess().waitFor();

        if (exitCode != 0) {
            logger.error("Command '{}' failed with exit code {}: {}", String.join(" ", command), exitCode, output.trim());
        } else {
            logger.debug("Command '{}' done: {}", String.join(" ", command), output.trim());
        }
    }

    private void runCommands(List<String[]> commands) {
        commandExecutor.execute(() -> {
            for (String[] command : commands) {
                try {
                    runCommand(command);
                } catch (Exception e) {
                    logger.error("Could not run '{}'", String.join(" ", command), e);
                }
            }
        });
    }

    // Package visible for the tests
    void waitForCommands() throws Exception {
        commandExecutor.submit(() -> {
        }).get();
    }

    // Package visible for the tests
    void writeFile(String path, String content) throws IOException {
        try (FileWriter fileWriter = new FileWriter(path, false)) {
            fileWriter.write(content);
        }
    }

    private void setSystemAudioOutput(int id) throws Exception {
        // TODO Not supported currently
        //ShellManager shellManager = new ShellManager(new String[]{"amixer", "cset", "numid=3", String.valueOf(id)});
        //shellManager.getProcess().waitFor();
    }

    // Package visible for the tests
    String getAlsaSettings(Settings settings) {
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
                writeFile(alsaSettingsPath, getAlsaSettings(settings));
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

    // Package visible for the tests
    String getHostapdConfig(Settings settings) {
        String apConfig = "";
        String countryCode = getWlanApCountryCode(settings);

        apConfig += "interface=wlan0\n";
        apConfig += "driver=nl80211\n";
        apConfig += "ssid=" + settings.getWlanApSsid() + "\n";
        apConfig += "utf8_ssid=1\n";
        apConfig += "hw_mode=" + settings.getWlanApHwMode() + "\n";
        apConfig += "channel=" + settings.getWlanApChannel() + "\n";
        if (countryCode != null) {
            apConfig += "country_code=" + countryCode + "\n";
        }
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

        return apConfig;
    }

    private void updateWlanAp(Settings settings) {
        String apConfig = getHostapdConfig(settings);
        String countryCode = getWlanApCountryCode(settings);
        boolean enable = Boolean.TRUE.equals(settings.getWlanApEnable());

        String wlanAp = apConfig + "|" + countryCode + "|" + enable;
        if (wlanAp.equals(appliedWlanAp)) {
            return;
        }
        appliedWlanAp = wlanAp;

        // Update the access point configuration
        String fileName = "/etc/hostapd/hostapd.conf";
        if (settingsService.isReadOnlyFileSystem()) {
            fileName = "/data/rocketshow/hostapd.conf";
        }

        try {
            writeFile(fileName, apConfig);
        } catch (IOException e) {
            logger.error("Could not write {}", fileName, e);
        }

        List<String[]> commands = new ArrayList<>();

        // Set the country in the raspberry settings (and temporarily remount /boot/firmware as rw to do so), before
        // starting the access point with it
        if (countryCode != null) {
            commands.add(new String[]{"sudo", new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "set-wifi-ap-country.sh", countryCode});
        }

        // Activate/deactivate the access point completely
        if (enable) {
            commands.add(new String[]{"sudo", "systemctl", "enable", "hostapd"});
            // Restart, even if already started, to cater for updated configs
            commands.add(new String[]{"sudo", "systemctl", "restart", "hostapd"});
        } else {
            commands.add(new String[]{"sudo", "systemctl", "disable", "hostapd"});
            commands.add(new String[]{"sudo", "systemctl", "stop", "hostapd"});
        }

        runCommands(commands);
    }

    private void updateLanIp(Settings settings) {
        String scriptPath = new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "set-lan-ip.sh";
        boolean isStatic = Boolean.TRUE.equals(settings.getLanStaticIpEnable());

        String[] command;
        if (isStatic) {
            command = new String[]{
                    "sudo", scriptPath, "static",
                    settings.getLanIpAddress() != null ? settings.getLanIpAddress() : "",
                    settings.getLanSubnetMask() != null ? settings.getLanSubnetMask() : "255.255.255.0",
                    settings.getLanGateway() != null ? settings.getLanGateway() : "",
                    settings.getLanDns1() != null ? settings.getLanDns1() : "",
                    settings.getLanDns2() != null ? settings.getLanDns2() : ""
            };
        } else {
            command = new String[]{"sudo", scriptPath, "dhcp"};
        }

        String lanIp = String.join("|", command);
        if (lanIp.equals(appliedLanIp)) {
            return;
        }
        appliedLanIp = lanIp;

        runCommands(List.<String[]>of(command));
    }

    private void updateOlaArtNetConf(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            logger.debug("Skipping OLA ArtNet conf update: no IP address available");
            return;
        }

        if (ipAddress.equals(appliedOlaArtNetIp)) {
            return;
        }
        appliedOlaArtNetIp = ipAddress;

        String confPath = settingsService.isReadOnlyFileSystem()
                ? "/data/ola/ola-artnet.conf"
                : "/etc/ola/ola-artnet.conf";

        runCommands(List.<String[]>of(new String[]{
                "sudo", "sed", "-i",
                "s/^ip[[:space:]]*=[[:space:]]*.*/ip = " + ipAddress + "/",
                confPath
        }));
    }

    private String getWlanApCountryCode(Settings settings) {
        if (settings.getWlanApCountryCode() != null && !settings.getWlanApCountryCode().isBlank()) {
            return settings.getWlanApCountryCode();
        }

        return null;
    }

    private void updateConnectionMode(Settings settings) {
        if (!OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        String mode = "http";

        if (Boolean.TRUE.equals(settings.getTlsEnable())) {
            mode = "https";
        }

        if (mode.equals(appliedConnectionMode)) {
            return;
        }
        appliedConnectionMode = mode;

        runCommands(List.<String[]>of(new String[]{"sudo", new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "set-connection-mode.sh", mode}));
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

            try {
                updateLanIp(settings);
            } catch (Exception e) {
                logger.error("Could not update the LAN IP settings", e);
            }

            try {
                updateOlaArtNetConf(lanService.getLanInfo().getIpAddress());
            } catch (Exception e) {
                logger.error("Could not update the OLA ArtNet conf", e);
            }

            try {
                lightingService.reloadOlaPlugins();
            } catch (Exception e) {
                logger.error("Could not reload OLA plugins", e);
            }
        }

        try {
            lightingService.enablePlugins(settings.getLightingOlaPluginList());
            lightingService.initializeUniverses();
        } catch (Exception e) {
            logger.error("Could not activate the OLA plugin", e);
        }

        updateConnectionMode(settings);
    }

}
