package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.SceneMidiMapping;
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

    // Play one scene of the project, by its uuid or its name, instead of the ones played so far: until it is stopped
    // (untilStopped, or without a duration) by other scenes or by turning all scenes off, or else for the passed time.
    // Then it is turned off again, unless other scenes were picked meanwhile.
    @PostMapping("play")
    public ResponseEntity<Void> playScene(
            @RequestParam("projectName") String projectName,
            @RequestParam("scene") String scene,
            @RequestParam(value = "untilStopped", required = false, defaultValue = "false") boolean untilStopped,
            @RequestParam(value = "durationMillis", required = false, defaultValue = "0") long durationMillis
    ) {
        scenePlayerService.playScene(projectName, scene, untilStopped ? ScenePlayerService.UNTIL_STOPPED : durationMillis);
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

    // Play one scene instead of the ones on so far, or turn it off, if it is on (like a tap on the Play screen)
    @PostMapping("toggle")
    public ResponseEntity<Void> toggleScene(@RequestParam("projectName") String projectName, @RequestParam("scene") String scene) {
        scenePlayerService.toggleScene(projectName, scene);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Light a scene on top of the others while it is held (on), and turn it off again
    @PostMapping("flash")
    public ResponseEntity<Void> flashScene(@RequestParam("projectName") String projectName, @RequestParam("scene") String scene, @RequestParam("on") boolean on) {
        scenePlayerService.flashScene(projectName, scene, on);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Play the next or the previous scene of a folder (by its uuid or its name, none = all scenes of the project)
    @PostMapping("next")
    public ResponseEntity<Void> nextScene(@RequestParam(value = "projectName", required = false) String projectName, @RequestParam(value = "folder", required = false) String folder) {
        scenePlayerService.stepScene(projectName, folder, 1);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("previous")
    public ResponseEntity<Void> previousScene(@RequestParam(value = "projectName", required = false) String projectName, @RequestParam(value = "folder", required = false) String folder) {
        scenePlayerService.stepScene(projectName, folder, -1);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The speed master, 1 = as written
    @PostMapping("speed")
    public ResponseEntity<Void> setSpeed(@RequestParam("value") double value) {
        scenePlayerService.setSpeed(value);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The dimmer of a scene on the Play screen, from 0 to 1
    @PostMapping("dimmer")
    public ResponseEntity<Void> setSceneDimmer(@RequestParam("scene") String scene, @RequestParam("value") double value) {
        scenePlayerService.setSceneDimmer(scene, value);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Wait for the key, pad or button of a MIDI controller to play the scene with ("toggle" or "flash")
    @PostMapping("learn")
    public ResponseEntity<Void> learn(@RequestParam("projectName") String projectName, @RequestParam("scene") String scene, @RequestParam(value = "mode", required = false, defaultValue = "toggle") String mode) {
        scenePlayerService.learn(projectName, scene, mode);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("learn-cancel")
    public ResponseEntity<Void> cancelLearn() {
        scenePlayerService.cancelLearn();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The keys, pads and buttons learned for the scenes of a project
    @GetMapping("midi")
    public List<SceneMidiMapping> getMidiMappings(@RequestParam("projectName") String projectName) {
        return scenePlayerService.getMidiMappings(projectName);
    }

    @PostMapping("midi-forget")
    public ResponseEntity<Void> forgetMidi(@RequestParam("projectName") String projectName, @RequestParam("scene") String scene, @RequestParam(value = "mode", required = false, defaultValue = "toggle") String mode) {
        scenePlayerService.forgetMidi(projectName, scene, mode);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
