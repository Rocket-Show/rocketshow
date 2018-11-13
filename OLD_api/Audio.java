package com.ascargon.rocketshow.api;

import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioUtil;

@Path("/audio")
public class Audio {

	@Context
	ServletContext context;

	@Path("devices")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<AudioDevice> getDevices() {
		return AudioUtil.getAudioDevices();
	}

}
