package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.FixtureService;
import com.ascargon.rocketshow.lighting.designer.Preview;
import com.ascargon.rocketshow.lighting.designer.SearchFixtureTemplate;
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

    @PostMapping("designer-preview")
    public void getFixture(@RequestBody Preview preview) {
        designerService.setPreviewPreset(preview.isPresetPreview());
        designerService.setSelectedPresetUuid(preview.getPresetUuid());
        designerService.setSelectedSceneUuids(preview.getSceneUuids());
    }

}
