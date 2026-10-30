package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.lighting.designer.DesignerJson;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.FixtureService;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.lighting.designer.SearchFixtureTemplate;
import com.ascargon.rocketshow.util.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}")
@CrossOrigin
public class DesignerController {

    private final static Logger logger = LoggerFactory.getLogger(DesignerController.class);

    private final ControllerService controllerService;
    private final FixtureService fixtureService;
    private final DesignerService designerService;
    private final SettingsService settingsService;
    private final CompositionService compositionService;
    private final PlayerService playerService;

    public DesignerController(ControllerService controllerService, FixtureService fixtureService, DesignerService designerService, SettingsService settingsService, CompositionService compositionService, PlayerService playerService) {
        this.controllerService = controllerService;
        this.fixtureService = fixtureService;
        this.designerService = designerService;
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.playerService = playerService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @GetMapping("fixtures")
    public List<SearchFixtureTemplate> searchFixtures(@RequestParam(value = "uuid", required = false) String uuid, @RequestParam(value = "manufacturerShortName", required = false) String manufacturerShortName, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "mainCategory", required = false) String mainCategory) throws Exception {
        return fixtureService.searchFixtures(uuid, manufacturerShortName, name, mainCategory);
    }

    @GetMapping("fixture")
    public String getFixture(@RequestParam("uuid") String uuid) throws Exception {
        return fixtureService.getFixture(uuid);
    }

    @PostMapping("preview")
    public synchronized void preview(
            @RequestBody String projectJson,
            @RequestParam("positionMillis") long positionMillis,
            @RequestParam(value = "compositionName", required = false, defaultValue = "") String compositionName
    ) {
        // If called with compositionName, we assume, the timeline is playing
        // and respect scene regions. If no compositionName is provided,
        // we just preview selected presets/scenes.

        if (!settingsService.getSettings().getDesignerLivePreview()) {
            logger.debug("Live preview disabled");
            return;
        }

        logger.debug("Preview designer...");

        // read like the projects saved on the unit, which the designer's own preview reads the same way
        Project project = DesignerJson.MAPPER.readValue(projectJson, Project.class);

        // Stop currently playing stuff
        designerService.stopPreview();
        designerService.load(null, project, null);

        designerService.setPreviewPreset(project.isPreviewPreset());
        designerService.setSelectedPresetUuid(project.getSelectedPresetUuid());
        designerService.setSelectedStepUuid(project.getSelectedStepUuid());
        designerService.setStepPreviewRunning(project.isStepPreviewRunning(), project.getStepPreviewStartMillis());
        designerService.setSelectedSceneUuids(project.getSelectedSceneUuids());
        designerService.setIdentifyFixtureUuids(project.getIdentifyFixtureUuids());
        designerService.setPreviewComposition(compositionName);

        designerService.startPreview(positionMillis);
    }

    @PostMapping("stop-preview-play")
    public synchronized void stopPreviewPlay() {
        if (!settingsService.getSettings().getDesignerLivePreview()) {
            return;
        }

        designerService.setPreviewComposition(null);
    }

    @GetMapping("project")
    public String getProject(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "id", required = false) String id
    ) throws IOException {
        // Return the string, because the parsed project might be missing some attributes
        // not needed in the backend

        // Return the template
        if ("1".equals(id)) {
            return new String(Files.readAllBytes(Paths.get(new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "designer_template.json")));
        }

        return new String(Files.readAllBytes(Paths.get(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath() + File.separator + FileNames.check(name) + ".json")));
    }

    @GetMapping("projects")
    public List<Project> getProjects() {
        return designerService.getAllProjects();
    }

    @PostMapping("project")
    public void saveProject(@RequestBody String project) throws Exception {
        designerService.saveProject(project);
        lightsChanged();
    }

    @DeleteMapping("project")
    public void deleteProject(@RequestParam("name") String name) throws Exception {
        designerService.deleteProjectByName(name);
        lightsChanged();
    }

    // A composition plays at least as long as its lights: the sets and the composition playing show how long now
    private void lightsChanged() throws Exception {
        compositionService.updateSets();
        playerService.compositionsChanged();
    }

    @PostMapping("update-profiles")
    public void updateProfiles() throws Exception {
        fixtureService.updateProfiles();
    }

}
