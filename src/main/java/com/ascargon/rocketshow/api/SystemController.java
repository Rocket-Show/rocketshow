package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.util.RebootService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/system")
class SystemController {

    private final StateService stateService;
    private final SetService setService;
    private final PlayerService playerService;
    private final RebootService rebootService;
    private final SettingsService settingsService;

    public SystemController(StateService stateService, SetService setService, PlayerService playerService, RebootService rebootService, SettingsService settingsService) {
        this.stateService = stateService;
        this.setService = setService;
        this.playerService = playerService;
        this.rebootService = rebootService;
        this.settingsService = settingsService;
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

//	@Path("reconnect-midi")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response reconnectMidi() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		manager.reconnectMidiDevices();
//		return Response.status(200).build();
//	}
//
//	@Path("current-version")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public VersionInfo version() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		return manager.getUpdater().getCurrentVersionInfo();
//	}
//
//	@Path("remote-version")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public VersionInfo remoteVersion() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		return manager.getUpdater().getRemoteVersionInfo();
//	}
//
//	@Path("update")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response update() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		manager.getUpdater().update();
//		return Response.status(200).build();
//	}

    @GetMapping("state")
    public com.ascargon.rocketshow.api.State getState() {
        return stateService.getCurrentState(playerService, setService);
    }

//	@Path("settings")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public Settings getSettings() {
//		Manager manager = (Manager)context.getAttribute("manager");
//		return manager.getSettings();
//	}
//
//	@Path("settings")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response saveSettings(Settings settings) throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		manager.setSettings(settings);
//		manager.saveSettings();
//
//		return Response.status(200).build();
//	}
//
//	@Path("factory-reset")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response factoryReset() throws Exception {
//		DefaultFactoryResetService.reset();
//
//		return Response.status(200).build();
//	}
//
//	@Path("download-logs")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response downloadLogs() throws Exception {
//		return Response.ok(DefaultLogDownloadService.getLogsFile(), "application/zip").build();
//	}
//
//	@Path("disk-space")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public DiskSpace getDiskSpace() throws Exception {
//		return DefaultDiskSpaceService.get();
//	}

}
