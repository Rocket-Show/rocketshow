package com.ascargon.rocketshow.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/transport")
public class TransportController {

    //private CompositionPlayerService player;
    private NotificationService notificationService;

    private final static Logger logger = LogManager.getLogger(TransportController.class);

    public TransportController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

//    @Path("load")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response load(@QueryParam("name") String compositionName) throws Exception {
//        logger.info("Received API request for transport/load");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//
//        if (compositionName.length() > 0) {
//            if (!manager.getPlayer().getCompositionName().equals(compositionName)) {
//
//                // Load the composition with the given name into the player
//                manager.getPlayer().setComposition(manager.getDefaultCompositionService().getComposition(compositionName),
//                        false, false);
//            }
//        }
//
//        // Load the files for the current composition
//        manager.getPlayer().load();
//
//        return Response.status(200).build();
//    }

    @PostMapping("play")
    public ResponseEntity<Void> play() throws Exception {
        logger.info("Received API request for transport/play");

        notificationService.notifyClients();

        return new ResponseEntity<Void>(HttpStatus.OK);

//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().play();
    }

//    @Path("play-as-sample")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response playAsSample(@QueryParam("name") String compositionName) throws Exception {
//        logger.info("Received API request for transport/play-as-sample");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().playAsSample(compositionName);
//
//        return Response.status(200).build();
//    }
//
//    @Path("pause")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response pause() throws Exception {
//        logger.info("Received API request for transport/pause");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().pause();
//
//        return Response.status(200).build();
//    }
//
//    @Path("toggle-play")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response togglePlay() throws Exception {
//        logger.info("Received API request for transport/toggle-play");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().togglePlay();
//
//        return Response.status(200).build();
//    }
//
//    @Path("stop")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response stop(@QueryParam("playDefaultComposition") @DefaultValue("true") boolean playDefaultComposition) throws Exception {
//
//        logger.info("Received API request for transport/stop");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().stop(playDefaultComposition);
//
//        return Response.status(200).build();
//    }
//
//    @Path("seek")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response seek(@QueryParam("positionMillis") long positionMillis) throws Exception {
//        logger.info("Received API request for transport/seek");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        manager.getPlayer().seek(positionMillis);
//
//        return Response.status(200).build();
//    }
//
//    @Path("next-composition")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response nextComposition() throws Exception {
//        logger.info("Received API request for transport/next-composition");
//        playerService.setNextComposition();
//        return Response.status(200).build();
//    }
//
//    @Path("previous-composition")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response previousComposition() throws Exception {
//        logger.info("Received API request for transport/previous-composition");
////      playerService.setPreviousComposition();
//        return Response.status(200).build();
//    }
//
//    @Path("set-composition-index")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response setCompositionIndex(@QueryParam("index") int index) throws Exception {
//        logger.info("Received API request for transport/set-composition-index");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//        if (manager.getCurrentSet() != null) {
//            manager.getCurrentSet().setCompositionIndex(index);
//        }
//        return Response.status(200).build();
//    }
//
//    @Path("set-composition-name")
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response setCompositionName(@QueryParam("name") String compositionName) throws Exception {
//        logger.info("Received API request for transport/set-composition-name");
//
//        Manager manager = (Manager) context.getAttribute("manager");
//
//        if (compositionName.length() > 0) {
//            manager.getPlayer().setCompositionName(compositionName);
//        }
//
//        return Response.status(200).build();
//    }

}
