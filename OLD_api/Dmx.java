package com.ascargon.rocketshow.api;

import javax.servlet.ServletContext;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ascargon.rocketshow.Manager;

@Path("/dmx")
public class Dmx {

	@Context
	ServletContext context;
    
	@Path("reset")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response reset() {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getDmxManager().reset();
		return Response.status(200).build();
	}
	
}
