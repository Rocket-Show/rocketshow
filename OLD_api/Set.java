package com.ascargon.rocketshow.api;

import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ascargon.rocketshow.Manager;

@Path("/set")
public class Set {

	@Context
	ServletContext context;
    
	@Path("list")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<com.ascargon.rocketshow.composition.Set> getAll() throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getCompositionManager().getAllSets();
	}
	
	@Path("load")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response load(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.loadSetAndComposition(name);
		return Response.status(200).build();
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.composition.Set getCurrent() {
		Manager manager = (Manager)context.getAttribute("manager");
		return manager.getCurrentSet();
	}
	
	@Path("details")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.composition.Set getByName(@QueryParam("name") String name) {
		Manager manager = (Manager)context.getAttribute("manager");
		return manager.getCompositionManager().getSet(name);
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response save(com.ascargon.rocketshow.composition.Set set) throws Exception {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getCompositionManager().saveSet(set);
		manager.loadSetAndComposition(set.getName());
		return Response.status(200).build();
	}
	
	@Path("delete")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response delete(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getCompositionManager().deleteSet(name);
		
		// Load the default set, if the current set has been deleted
		if(manager.getCurrentSet() != null) {
			if(manager.getCurrentSet().getName().equals(name)) {
				manager.loadSetAndComposition("");
			}
		}
		
		return Response.status(200).build();
	}
	
}
