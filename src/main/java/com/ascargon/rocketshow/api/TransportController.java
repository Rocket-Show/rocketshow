package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.SetService;
import org.apache.catalina.Manager;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/transport")
class TransportController {

    private final static Logger logger = LoggerFactory.getLogger(TransportController.class);

    private final NotificationService notificationService;
    private final PlayerService playerService;
    private final SetService setService;

    private TransportController(NotificationService notificationService, PlayerService playerService, SetService setService) {
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.setService = setService;
    }

    @PostMapping("load")
    public ResponseEntity<Void> load(@RequestParam("name") String compositionName) throws Exception {
        logger.info("Received API request for transport/load");

        playerService.loadCompositionName(compositionName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("play")
    public ResponseEntity<Void> play() throws Exception {
        logger.info("Received API request for transport/play");

        playerService.play();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("play-as-sample")
    public ResponseEntity<Void> playAsSample(@RequestParam("name") String compositionName) throws Exception {
        logger.info("Received API request for transport/play-as-sample");

        playerService.playAsSample(compositionName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("pause")
    public ResponseEntity<Void> pause() throws Exception {
        logger.info("Received API request for transport/pause");

        playerService.pause();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("toggle-play")
    public ResponseEntity<Void> togglePlay() throws Exception {
        logger.info("Received API request for transport/toggle-play");

        playerService.togglePlay();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("stop")
    public ResponseEntity<Void> stop(@RequestParam(value = "playDefaultComposition", required = false, defaultValue = "true") boolean playDefaultComposition) throws Exception {
        logger.info("Received API request for transport/stop");

        playerService.stop(playDefaultComposition);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("seek")
    public ResponseEntity<Void> seek(@RequestParam("positionMillis") long positionMillis) throws Exception {
        logger.info("Received API request for transport/seek");

        playerService.seek(positionMillis);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("next-composition")
    public ResponseEntity<Void> nextComposition() throws Exception {
        logger.info("Received API request for transport/next-composition");

        playerService.setNextComposition();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("previous-composition")
    public ResponseEntity<Void> previousComposition() throws Exception {
        logger.info("Received API request for transport/previous-composition");

        playerService.setPreviousComposition();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("set-composition-index")
    public ResponseEntity<Void> setCompositionIndex(@RequestParam("index") int compositionIndex) throws Exception {
        logger.info("Received API request for transport/set-composition-index");

        setService.setCurrentCompositionIndex(compositionIndex);
        playerService.setCompositionName(setService.getCurrentCompositionName());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("set-composition-name")
    public ResponseEntity<Void> setCompositionName(@RequestParam("name") String compositionName) throws Exception {
        logger.info("Received API request for transport/set-composition-name");

        playerService.setCompositionName(compositionName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
