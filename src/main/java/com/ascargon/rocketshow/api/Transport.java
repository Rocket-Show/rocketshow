package com.ascargon.rocketshow.api;

import javax.servlet.ServletContext;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

@Path("/transport")
public class Transport {

	final static Logger logger = Logger.getLogger(Transport.class);

	@Context
	ServletContext context;

	@Path("load")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response load(@QueryParam("name") String compositionName, @QueryParam("position") @DefaultValue("0") Long position) throws Exception {
		logger.info("Received API request for transport/load");

		Manager manager = (Manager) context.getAttribute("manager");

		if (compositionName.length() > 0) {
			if (!manager.getPlayer().getCompositionName().equals(compositionName)) {
				// Load the composition with the given name into the player
				manager.getPlayer().setComposition(manager.getCompositionManager().loadComposition(compositionName), false);
			}
		}

		// Load the files for the current composition
		manager.getPlayer().loadFiles(position);

		return Response.status(200).build();
	}

	@Path("play")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response play() throws Exception {
		logger.info("Received API request for transport/play");

		Manager manager = (Manager) context.getAttribute("manager");
		manager.getPlayer().play();
		
		return Response.status(200).build();
	}

	@Path("pause")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response pause() throws Exception {
		logger.info("Received API request for transport/pause");

		Manager manager = (Manager) context.getAttribute("manager");
		manager.getPlayer().pause();

		return Response.status(200).build();
	}

	@Path("toggle-play")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response togglePlay() throws Exception {
		logger.info("Received API request for transport/toggle-play");

		Manager manager = (Manager) context.getAttribute("manager");
		manager.getPlayer().togglePlay();
		
		return Response.status(200).build();
	}

	@Path("stop")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response stop() throws Exception {
		logger.info("Received API request for transport/stop");

		Manager manager = (Manager) context.getAttribute("manager");
		manager.getPlayer().stop();
		
		return Response.status(200).build();
	}
	
	@Path("seek")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response seek(@QueryParam("position") long position) throws Exception {
		logger.info("Received API request for transport/seek");

		Manager manager = (Manager) context.getAttribute("manager");
		manager.getPlayer().seek(position);
		
		return Response.status(200).build();
	}

	@Path("next-composition")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response nextComposition() throws Exception {
		logger.info("Received API request for transport/next-composition");

		Manager manager = (Manager) context.getAttribute("manager");
		if (manager.getCurrentSet() != null) {
			manager.getCurrentSet().nextComposition();
		}
		return Response.status(200).build();
	}

	@Path("previous-composition")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response previousComposition() throws Exception {
		logger.info("Received API request for transport/previous-composition");

		Manager manager = (Manager) context.getAttribute("manager");
		if (manager.getCurrentSet() != null) {
			manager.getCurrentSet().previousComposition();
		}
		return Response.status(200).build();
	}

	@Path("set-composition-index")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response setCompositionIndex(@QueryParam("index") int index) throws Exception {
		logger.info("Received API request for transport/set-composition-index");

		Manager manager = (Manager) context.getAttribute("manager");
		if (manager.getCurrentSet() != null) {
			manager.getCurrentSet().setCompositionIndex(index);
		}
		return Response.status(200).build();
	}

	@Path("set-composition-name")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response setCompositionName(@QueryParam("name") String compositionName) throws Exception {
		logger.info("Received API request for transport/set-composition-name");

		Manager manager = (Manager) context.getAttribute("manager");

		if (compositionName.length() > 0) {
			manager.getPlayer().setCompositionName(compositionName);
		}

		return Response.status(200).build();
	}
	
}
