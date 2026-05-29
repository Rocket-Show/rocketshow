package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.OlaPlugin;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.ApiKey;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.SettingsUpdateSystemService;
import com.ascargon.rocketshow.update.UpdateService;
import com.ascargon.rocketshow.update.UpdateState;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.*;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/system")
@CrossOrigin
class SystemController {

    private final static Logger logger = LoggerFactory.getLogger(SystemController.class);

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final ControllerService controllerService;
    private final StateService stateService;
    private final SetService setService;
    private final PlayerService playerService;
    private final RebootService rebootService;
    private final ShutdownService shutdownService;
    private final SettingsService settingsService;
    private final MidiDeviceInService midiDeviceInService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final UpdateService updateService;
    private final FactoryResetService factoryResetService;
    private final LogDownloadService logDownloadService;
    private final DiskSpaceService diskSpaceService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final CompositionService compositionService;
    private final DesignerService designerService;
    private final BackupService backupService;
    private final SettingsUpdateSystemService settingsUpdateSystemService;
    private final ActionExecutionService actionExecutionService;
    private final DeviceInformationService deviceInformationService;
    private final HealthService healthService;
    private final VersionService versionService;
    private final SshService sshService;
    private final LightingService lightingService;

    public SystemController(
            ControllerService controllerService,
            StateService stateService,
            SetService setService,
            PlayerService playerService,
            RebootService rebootService,
            ShutdownService shutdownService,
            SettingsService settingsService,
            MidiDeviceInService midiDeviceInService,
            MidiDeviceOutService midiDeviceOutService,
            UpdateService updateService,
            FactoryResetService factoryResetService,
            LogDownloadService logDownloadService,
            DiskSpaceService diskSpaceService,
            OperatingSystemInformationService operatingSystemInformationService,
            CompositionService compositionService,
            DesignerService designerService,
            BackupService backupService,
            SettingsUpdateSystemService settingsUpdateSystemService,
            ActionExecutionService actionExecutionService,
            DeviceInformationService deviceInformationService,
            HealthService healthService,
            VersionService versionService,
            SshService sshService,
            LightingService lightingService
    ) {
        this.controllerService = controllerService;
        this.stateService = stateService;
        this.setService = setService;
        this.playerService = playerService;
        this.rebootService = rebootService;
        this.shutdownService = shutdownService;
        this.settingsService = settingsService;
        this.midiDeviceInService = midiDeviceInService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.updateService = updateService;
        this.factoryResetService = factoryResetService;
        this.logDownloadService = logDownloadService;
        this.diskSpaceService = diskSpaceService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.compositionService = compositionService;
        this.designerService = designerService;
        this.backupService = backupService;
        this.settingsUpdateSystemService = settingsUpdateSystemService;
        this.actionExecutionService = actionExecutionService;
        this.deviceInformationService = deviceInformationService;
        this.healthService = healthService;
        this.versionService = versionService;
        this.sshService = sshService;
        this.lightingService = lightingService;
    }

    private void settingsUpdateSystem() {
        settingsUpdateSystemService.update();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @PostMapping("reboot")
    public ResponseEntity<Void> reboot() throws Exception {
        rebootService.reboot();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("shutdown")
    public ResponseEntity<Void> shutdown() throws Exception {
        shutdownService.shutdown();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("enable-ssh")
    public ResponseEntity<String> enableSsh() throws Exception {
        return ResponseEntity.ok(sshService.enableSsh());
    }

    @PostMapping("reload-settings")
    public ResponseEntity<Void> reloadSettings() throws Exception {
        settingsService.load();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // TODO Move to MIDI controller
    @PostMapping("reconnect-midi")
    public ResponseEntity<Void> reconnectMidi() throws Exception {
        midiDeviceOutService.reconnectMidiDevice();
        midiDeviceInService.reconnectMidiDevice();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("current-version")
    public VersionInfo version() throws Exception {
        return versionService.getCurrentVersionInfo();
    }

    @GetMapping("remote-version")
    public VersionInfo remoteVersion(@RequestParam(value = "testBranch", required = false, defaultValue = "false") boolean testBranch) throws Exception {
        return versionService.getRemoteVersionInfo(testBranch);
    }

    @PostMapping("update")
    public ResponseEntity<Void> update(@RequestParam(value = "testBranch", required = false, defaultValue = "false") boolean testBranch) throws Exception {
        updateService.update(testBranch);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("state")
    public com.ascargon.rocketshow.api.State getState() {
        return stateService.getCurrentState(
                playerService,
                setService,
                compositionService
        );
    }

    @GetMapping("update-state")
    public UpdateState getUpdateState() {
        return updateService.getCurrentState();
    }

    @GetMapping("settings")
    public Settings getSettings() {
        return settingsService.getSettings();
    }

    @PostMapping("settings")
    public ResponseEntity<Void> saveSettings(@RequestBody Settings settings) throws JAXBException {
        // Process API keys
        for (ApiKey apiKey : settings.getApiKeyList()) {
            if (apiKey.getKey() != null && !apiKey.getKey().isEmpty()) {
                // New key: Hash it
                apiKey.setKeyHash(encoder.encode(apiKey.getKey()));
                apiKey.setKey(null);
            } else {
                // Existing key: Keep the old hash (not loaded to the frontend)
                for (ApiKey existingApiKey : settingsService.getSettings().getApiKeyList()) {
                    if (existingApiKey.getUuid().equals(apiKey.getUuid())) {
                        apiKey.setKeyHash(existingApiKey.getKeyHash());
                        break;
                    }
                }
            }
        }

        // Preserve the admin hash
        settings.setAdminPasswordHash(settingsService.getSettings().getAdminPasswordHash());

        settingsService.setSettings(settings);
        settingsService.save();
        settingsUpdateSystem();

        if (settings.getDesignerLivePreview()) {
            designerService.startPreview(0);
        } else {
            designerService.stopPreview();
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("settings-lighting-ola-plugins")
    public ResponseEntity<Void> updateLightingOlaPlugins(@RequestBody List<OlaPlugin> olaPluginList) throws JAXBException {
        settingsService.getSettings().setLightingOlaPluginList(olaPluginList);
        settingsService.save();
        settingsUpdateSystem();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("settings-lighting-universes")
    public ResponseEntity<Void> updateLightingUniverses(@RequestBody List<LightingUniverse> lightingUniverseList) throws JAXBException {
        lightingService.updateUniverses(lightingUniverseList);
        settingsService.getSettings().setLightingUniverseList(lightingUniverseList);
        settingsService.save();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("factory-reset")
    public ResponseEntity<Void> factoryReset() throws Exception {
        factoryResetService.reset();
        rebootService.reboot();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("download-logs")
    public ResponseEntity<Resource> downloadLogs() throws Exception {
        InputStreamResource resource = new InputStreamResource(new FileInputStream(logDownloadService.getLogsFile()));
        return ResponseEntity.ok().body(resource);
    }

    @GetMapping("disk-space")
    public DiskSpace getDiskSpace() throws Exception {
        return diskSpaceService.get();
    }

    @GetMapping("operating-system-information")
    public OperatingSystemInformation getOperatingSystemInformation() {
        return operatingSystemInformationService.getOperatingSystemInformation();
    }

    @GetMapping("create-backup")
    public ResponseEntity<Resource> createBackup() throws Exception {
        InputStreamResource resource = new InputStreamResource(new FileInputStream(backupService.create()));
        return ResponseEntity.ok().body(resource);
    }

    @PostMapping("restore-backup")
    public ResponseEntity<Void> restoreBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam("dzchunkindex") Long dzchunkindex,
            @RequestParam("dztotalchunkcount") Long dztotalchunkcount
    ) throws Exception {
        if (dzchunkindex == 0) {
            backupService.restoreInit();
        }
        backupService.restoreAddChunk(file.getInputStream());
        if (dzchunkindex.equals(dztotalchunkcount - 1)) {
            backupService.restoreFinish();
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("execute-action")
    public ResponseEntity<Void> executeAction(@RequestBody Action action) throws Exception {
        // ensure, the action is only executed locally
        action.setExecuteLocally(true);
        action.setRemoteDeviceNames(new ArrayList<>());

        actionExecutionService.execute(action);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("device-information")
    public DeviceInformation getDeviceInformation() throws Exception {
        return deviceInformationService.getDeviceInformation();
    }

    @PostMapping("device-information")
    public ResponseEntity<Void> storeDeviceInformation(@RequestBody DeviceInformation deviceInformation) throws Exception {
        deviceInformationService.storeDeviceInformation(deviceInformation);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("lan-info")
    public LanInfo getLanInfo() {
        String ipAddress = "";
        String subnetMask = "";
        String gateway = "";
        String dns1 = "";
        String dns2 = "";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nmcli", "-t", "-f", "IP4.ADDRESS,IP4.GATEWAY,IP4.DNS,DHCP4.OPTION", "device", "show", "eth0");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Collect all assigned addresses so we can match by DHCP lease later
            List<String[]> addressList = new ArrayList<>(); // [ip, dotted-mask]
            List<String> dnsList = new ArrayList<>();
            String dhcpLeaseIp = null;
            String dhcpLeaseMask = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("IP4.ADDRESS[")) {
                        String cidr = line.substring(line.indexOf(':') + 1);
                        int slash = cidr.indexOf('/');
                        if (slash > 0) {
                            String ip = cidr.substring(0, slash);
                            String mask = prefixToMask(Integer.parseInt(cidr.substring(slash + 1)));
                            addressList.add(new String[]{ip, mask});
                        }
                    } else if (line.startsWith("IP4.GATEWAY:")) {
                        String gw = line.substring("IP4.GATEWAY:".length());
                        if (!"--".equals(gw)) gateway = gw;
                    } else if (line.startsWith("IP4.DNS[")) {
                        String dns = line.substring(line.indexOf(':') + 1);
                        if (!"--".equals(dns)) dnsList.add(dns);
                    } else if (line.startsWith("DHCP4.OPTION[")) {
                        // Format: "DHCP4.OPTION[N]:key = value"
                        String opt = line.substring(line.indexOf(':') + 1);
                        if (opt.startsWith("ip_address = ")) {
                            dhcpLeaseIp = opt.substring("ip_address = ".length());
                        } else if (opt.startsWith("subnet_mask = ")) {
                            dhcpLeaseMask = opt.substring("subnet_mask = ".length());
                        }
                    }
                }
            }

            process.waitFor();

            // Prefer the DHCP-leased address – it is the one the router actually knows.
            // Fall back to the first address in the list (static or link-local).
            if (dhcpLeaseIp != null && !dhcpLeaseIp.isEmpty()) {
                ipAddress = dhcpLeaseIp;
                if (dhcpLeaseMask != null && !dhcpLeaseMask.isEmpty()) {
                    subnetMask = dhcpLeaseMask;
                } else {
                    for (String[] entry : addressList) {
                        if (dhcpLeaseIp.equals(entry[0])) {
                            subnetMask = entry[1];
                            break;
                        }
                    }
                }
            } else if (!addressList.isEmpty()) {
                ipAddress = addressList.get(0)[0];
                subnetMask = addressList.get(0)[1];
            }

            if (!dnsList.isEmpty()) dns1 = dnsList.get(0);
            if (dnsList.size() > 1) dns2 = dnsList.get(1);

        } catch (Exception e) {
            logger.warn("Could not read LAN info via nmcli, falling back to NetworkInterface", e);
            try {
                NetworkInterface eth0 = NetworkInterface.getByName("eth0");
                if (eth0 != null) {
                    for (InterfaceAddress addr : eth0.getInterfaceAddresses()) {
                        if (addr.getAddress() instanceof Inet4Address) {
                            ipAddress = addr.getAddress().getHostAddress();
                            subnetMask = prefixToMask(addr.getNetworkPrefixLength());
                            break;
                        }
                    }
                }
            } catch (SocketException se) {
                logger.warn("Could not determine eth0 address via NetworkInterface", se);
            }
        }

        return new LanInfo(ipAddress, subnetMask, gateway, dns1, dns2);
    }

    private static String prefixToMask(int prefix) {
        if (prefix <= 0) return "0.0.0.0";
        if (prefix >= 32) return "255.255.255.255";
        int mask = ~(0xffffffff >>> prefix);
        return String.format("%d.%d.%d.%d",
                (mask >>> 24) & 0xff, (mask >>> 16) & 0xff,
                (mask >>> 8) & 0xff, mask & 0xff);
    }

    @GetMapping("health")
    public HealthStatus getHealth() {
        return healthService.getHealthStatus();
    }

    @PostMapping("test")
    public ResponseEntity<Void> test() {
        healthService.testSystem();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
