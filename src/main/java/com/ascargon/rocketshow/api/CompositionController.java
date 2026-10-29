package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.TestPlayResponse;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/composition")
@CrossOrigin
public class CompositionController {

    private final ControllerService controllerService;
    private final CompositionService compositionService;
    private final PlayerService playerService;
    private final DesignerService designerService;
    private final SettingsService settingsService;

    public CompositionController(ControllerService controllerService, CompositionService compositionService, PlayerService playerService, DesignerService designerService, SettingsService settingsService) {
        this.controllerService = controllerService;
        this.compositionService = compositionService;
        this.playerService = playerService;
        this.designerService = designerService;
        this.settingsService = settingsService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @GetMapping("list")
    public List<Composition> getAll() {
        return compositionService.getAllCompositions();
    }

    @GetMapping
    public Composition get(@RequestParam("name") String name) {
        return compositionService.getComposition(name);
    }

    // Saves the composition. With its previous name, it's renamed: the sets, designer projects, settings and actions
    // using it keep it under its new name.
    @PostMapping
    public ResponseEntity<Void> save(@RequestBody Composition composition, @RequestParam(value = "previousName", required = false) String previousName) throws Exception {
        if (previousName != null && !previousName.isEmpty() && !previousName.equals(composition.getName())) {
            compositionService.renameComposition(previousName, composition);
            designerService.renameComposition(previousName, composition.getName());
            settingsService.renameComposition(previousName, composition.getName());
            playerService.compositionRenamed(previousName, composition.getName());
        } else {
            compositionService.saveComposition(composition);

            // Doesn't stop the composition playing, e.g. when the next song is changed during a show
            playerService.compositionsChanged();
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("delete")
    public ResponseEntity<Void> delete(@RequestParam("name") String name) throws Exception {
        compositionService.deleteComposition(name);
        playerService.compositionsChanged();

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("test-play")
    public TestPlayResponse testPlay(@RequestBody Composition composition) throws Exception {
        TestPlayResponse testPlayResponse = new TestPlayResponse();
        compositionService.analyzeComposition(composition);
        playerService.playTestComposition(composition);
        testPlayResponse.setDurationMillis(playerService.getTestCompositionPlayer().getDurationMillis());
        return testPlayResponse;
    }

    @PostMapping("test-stop")
    public ResponseEntity<Void> testStop() throws Exception {
        playerService.stopTestComposition();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("test-pause")
    public ResponseEntity<Void> testPause() throws Exception {
        playerService.getTestCompositionPlayer().pause();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("test-seek")
    public ResponseEntity<Void> testSeek(@RequestParam("positionMillis") long positionMillis) throws Exception {
        playerService.getTestCompositionPlayer().seek(positionMillis);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
