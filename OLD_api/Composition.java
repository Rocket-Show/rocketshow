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

@Path("/composition")
public class Composition {

	@Context
	ServletContext context;

	@Path("list")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<com.ascargon.rocketshow.composition.Composition> getAll() throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getCompositionManager().getAllCompositions();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.composition.Composition get(@QueryParam("name") String name) {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getCompositionManager().getComposition(name);
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response save(com.ascargon.rocketshow.composition.Composition composition) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getCompositionManager().saveComposition(composition);

		// If this is the current composition, read it again
		if (manager.getPlayer().getCompositionName() != null
				&& manager.getPlayer().getCompositionName().equals(composition.getName())) {
			
			manager.getPlayer().setComposition(manager.getCompositionManager().getComposition(composition.getName()),
					true, true);
		}

		// Refresh the current set
		if (manager.getCurrentSet() != null) {
			manager.loadSetAndComposition(manager.getCurrentSet().getName());
		}

		return Response.status(200).build();
	}

	@Path("delete")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response delete(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getCompositionManager().deleteComposition(name);

		if (manager.getCurrentSet() != null) {
			manager.loadSetAndComposition(manager.getCurrentSet().getName());
		}

		return Response.status(200).build();
	}

}
