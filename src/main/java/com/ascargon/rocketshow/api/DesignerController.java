package com.ascargon.rocketshow.api;

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

    public DesignerController(FixtureService fixtureService, DesignerService designerService) {
        this.fixtureService = fixtureService;
        this.designerService = designerService;
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
    public void getFixture(@RequestBody Preview preview) {
        if (!designerService.getCurrentProject().getName().equals(preview.getPresetUuid())) {
            designerService.load(null, designerService.getProjectByName(preview.getProjectName()), null);
        }
        designerService.setPreviewPreset(preview.isPresetPreview());
        designerService.setSelectedPresetUuid(preview.getPresetUuid());
        designerService.setSelectedSceneUuids(preview.getSceneUuids());
    }

    @GetMapping("project")
    public Project getProject() {
        return
    }

}
