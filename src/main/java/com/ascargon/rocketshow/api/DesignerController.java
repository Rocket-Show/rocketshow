package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.lighting.designer.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}")
@CrossOrigin
public class DesignerController {

    private final FixtureService fixtureService;
    private final DesignerService designerService;
    private final SettingsService settingsService;

    public DesignerController(FixtureService fixtureService, DesignerService designerService, SettingsService settingsService) {
        this.fixtureService = fixtureService;
        this.designerService = designerService;
        this.settingsService = settingsService;
    }

    @GetMapping("fixtures")
    public List<SearchFixtureTemplate> searchFixtures(@RequestParam(value = "uuid", required = false) String uuid, @RequestParam(value = "manufacturerShortName", required = false) String manufacturerShortName, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "mainCategory", required = false) String mainCategory) throws IOException {
        return fixtureService.searchFixtures(uuid, manufacturerShortName, name, mainCategory);
    }

    @GetMapping("fixture")
    public String getFixture(@RequestParam("uuid") String uuid) throws IOException {
        return fixtureService.getFixture(uuid);
    }

    @PostMapping("preview")
    public void preview(@RequestBody Project project) {
        if(!settingsService.getSettings().getDesignerLivePreview()) {
            return;
        }
        designerService.stopPreview();
        designerService.load(null, project, null);
        designerService.setPreviewPreset(project.isPreviewPreset());
        designerService.setSelectedPresetUuid(project.getSelectedPresetUuid());
        designerService.setSelectedSceneUuids(project.getSelectedSceneUuids());
        designerService.startPreview();
    }

    @GetMapping("project")
    public Project getProject(@RequestParam("name") String name) {
        return designerService.getProjectByName(name);
    }

    @GetMapping("projects")
    public List<Project> getProjects() {
        return designerService.getAllProjects();
    }

    @PostMapping("project")
    public void saveProject(@RequestBody String project) throws IOException {
        designerService.saveProject(project);
    }

}
