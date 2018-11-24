package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.Settings;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/system")
class SystemController {

    private final StateService stateService;
    private final SetService setService;
    private final PlayerService playerService;
    private final RebootService rebootService;
    private final SettingsService settingsService;
    private final MidiDeviceInService midiDeviceInService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final UpdateService updateService;
    private final FactoryResetService factoryResetService;
    private final LogDownloadService logDownloadService;
    private final DiskSpaceService diskSpaceService;

    public SystemController(StateService stateService, SetService setService, PlayerService playerService, RebootService rebootService, SettingsService settingsService, MidiDeviceInService midiDeviceInService, MidiDeviceOutService midiDeviceOutService, UpdateService updateService, FactoryResetService factoryResetService, LogDownloadService logDownloadService, DiskSpaceService diskSpaceService) {
        this.stateService = stateService;
        this.setService = setService;
        this.playerService = playerService;
        this.rebootService = rebootService;
        this.settingsService = settingsService;
        this.midiDeviceInService = midiDeviceInService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.updateService = updateService;
        this.factoryResetService = factoryResetService;
        this.logDownloadService = logDownloadService;
        this.diskSpaceService = diskSpaceService;
    }

    @PostMapping("reboot")
    public ResponseEntity<Void> reboot() throws Exception {
        rebootService.reboot();
        return new ResponseEntity<>(HttpStatus.OK);
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
        return updateService.getCurrentVersionInfo();
    }

    @GetMapping("remote-version")
    public VersionInfo remoteVersion() throws Exception {
        return updateService.getRemoteVersionInfo();
    }

    @PostMapping("update")
    public ResponseEntity<Void> update() throws Exception {
        updateService.update();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("state")
    public com.ascargon.rocketshow.api.State getState() {
        return stateService.getCurrentState(playerService, setService);
    }

    @GetMapping("settings")
    public Settings getSettings() {
        return settingsService.getSettings();
    }

    @PostMapping("settings")
    public ResponseEntity<Void> saveSettings(Settings settings) {
        settingsService.setSettings(settings);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("factory-reset")
    public ResponseEntity<Void> factoryReset() throws Exception {
        factoryResetService.reset();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("download-logs")
    public ResponseEntity<File> downloadLogs() throws Exception {
        return ResponseEntity
                .ok()
                .contentType(new MediaType("application/zip"))
                .body(logDownloadService.getLogsFile());
    }

    @GetMapping("disk-space")
    public DiskSpace getDiskSpace() throws Exception {
        return diskSpaceService.get();
    }

}
