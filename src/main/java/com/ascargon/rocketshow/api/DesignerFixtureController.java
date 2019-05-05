package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.FixtureService;
import com.ascargon.rocketshow.lighting.designer.SearchFixtureTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}")
@CrossOrigin
public class DesignerFixtureController {

    private final FixtureService fixtureService;

    public DesignerFixtureController(FixtureService fixtureService) {
        this.fixtureService = fixtureService;
    }

    @GetMapping("fixture-search")
    public List<SearchFixtureTemplate> searchFixtures(@RequestParam(value = "uuid", required = false) String uuid, @RequestParam(value = "manufacturerShortName", required = false) String manufacturerShortName, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "mainCategory", required = false) String mainCategory) throws IOException {
        return fixtureService.searchFixtures(uuid, manufacturerShortName, name, mainCategory);
    }

    @GetMapping("fixture")
    public String getFixture(@RequestParam("uuid") String uuid) throws IOException {
        return fixtureService.getFixture(uuid);
    }

}
