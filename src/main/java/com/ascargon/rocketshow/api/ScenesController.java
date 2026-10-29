package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.SceneProject;
import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The scenes of the designer projects, picked by hand on the Play screen or by other devices (see ScenePlayerService).
 * Every app is told about a change with the state of the device.
 */
@RestController()
@RequestMapping("${spring.data.rest.base-path}/scenes")
@CrossOrigin
public class ScenesController {

    private final ControllerService controllerService;
    private final ScenePlayerService scenePlayerService;

    public ScenesController(ControllerService controllerService, ScenePlayerService scenePlayerService) {
        this.controllerService = controllerService;
        this.scenePlayerService = scenePlayerService;
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
    public ResponseEntity<Void> setActiveScenes(@RequestParam("projectName") String projectName, @RequestBody List<String> sceneUuids) {
        scenePlayerService.setActiveScenes(projectName, sceneUuids);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Play one scene of the project, by its uuid or its name, instead of the ones played so far. After the passed time
    // (0 = none) it is turned off again, unless other scenes were picked meanwhile.
    @PostMapping("play")
    public ResponseEntity<Void> playScene(
            @RequestParam("projectName") String projectName,
            @RequestParam("scene") String scene,
            @RequestParam(value = "durationMillis", required = false, defaultValue = "0") long durationMillis
    ) {
        scenePlayerService.playScene(projectName, scene, durationMillis);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Turn all scenes off
    @PostMapping("release")
    public ResponseEntity<Void> releaseAll() {
        scenePlayerService.releaseAll();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("blackout")
    public ResponseEntity<Void> setBlackout(@RequestParam("value") boolean value) {
        scenePlayerService.setBlackout(value);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("toggle-blackout")
    public ResponseEntity<Void> toggleBlackout() {
        scenePlayerService.toggleBlackout();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The master dimmer of the Play screen, from 0 to 1
    @PostMapping("master-dimmer")
    public ResponseEntity<Void> setMasterDimmer(@RequestParam("value") double value) {
        scenePlayerService.setMasterDimmer(value);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
