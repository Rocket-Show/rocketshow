package com.ascargon.rocketshow.api;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/session")
public class Session {

	@Context
	ServletContext context;
   
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.Session getSession() {
		Manager manager = (Manager)context.getAttribute("manager");
		return manager.getSession();
	}
	
	@Path("wizard-finished")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response setWizardFinished() {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getSession().setFirstStart(false);
		manager.saveSession();
		return Response.status(200).build();
	}
	
	@Path("dismiss-update-finished")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response dismissUpdateFinished() {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getSession().setUpdateFinished(false);
		manager.saveSession();
		return Response.status(200).build();
	}
	
	@Path("set-auto-select-next-composition")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response setAutoSelectNextComposition(@QueryParam("value") boolean value) {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getSession().setAutoSelectNextComposition(value);
		manager.saveSession();
		return Response.status(200).build();
	}
	
}
