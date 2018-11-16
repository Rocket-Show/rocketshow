package com.ascargon.rocketshow.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/system")
public class SystemController {

    private StateService stateService;

    public SystemController(StateService stateService) {
        this.stateService = stateService;
    }

//	@Path("reboot")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response reboot() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		manager.reboot();
//		return Response.status(200).build();
//	}
//
//	@Path("reload-settings")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response reloadSettings() throws Exception {
//		Manager manager = (Manager)context.getAttribute("manager");
//		manager.loadSettings();
//		return Response.status(200).build();
//	}
//
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
        return stateService.getCurrentState();
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
//		FactoryReset.reset();
//
//		return Response.status(200).build();
//	}
//
//	@Path("download-logs")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response downloadLogs() throws Exception {
//		return Response.ok(LogDownload.getLogsFile(), "application/zip").build();
//	}
//
//	@Path("disk-space")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public DiskSpace getDiskSpace() throws Exception {
//		return DiskSpaceUtil.get();
//	}

}
