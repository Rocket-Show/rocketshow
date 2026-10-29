package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.lighting.designer.SceneProject;
import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import com.ascargon.rocketshow.play.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The scenes of the designer projects, picked by hand on the Play screen (see ScenePlayerService).
 */
@RestController()
@RequestMapping("${spring.data.rest.base-path}/scenes")
@CrossOrigin
public class ScenesController {

    private final ControllerService controllerService;
    private final ScenePlayerService scenePlayerService;
    private final NotificationService notificationService;
    private final PlayerService playerService;
    private final SetService setService;

    public ScenesController(ControllerService controllerService, ScenePlayerService scenePlayerService, NotificationService notificationService, PlayerService playerService, SetService setService) {
        this.controllerService = controllerService;
        this.scenePlayerService = scenePlayerService;
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.setService = setService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @GetMapping("projects")
    public List<SceneProject> getProjects() {
        return scenePlayerService.getProjects();
    }

    // Play the passed scenes of the project instead of the ones played so far (none = release them all)
    @PostMapping("active")
    public ResponseEntity<Void> setActiveScenes(@RequestParam("projectName") String projectName, @RequestBody List<String> sceneUuids) throws Exception {
        scenePlayerService.setActiveScenes(projectName, sceneUuids);
        notificationService.notifyClients(playerService, setService);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("blackout")
    public ResponseEntity<Void> setBlackout(@RequestParam("value") boolean value) throws Exception {
        scenePlayerService.setBlackout(value);
        notificationService.notifyClients(playerService, setService);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The master dimmer of the Play screen, from 0 to 1
    @PostMapping("master-dimmer")
    public ResponseEntity<Void> setMasterDimmer(@RequestParam("value") double value) throws Exception {
        scenePlayerService.setMasterDimmer(value);
        notificationService.notifyClients(playerService, setService);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
