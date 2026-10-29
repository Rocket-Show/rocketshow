package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.DefaultFileFilterService;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Loading, saving, deleting and renaming the designer projects of a unit.
 */
class DesignerProjectsTest {

    @TempDir
    Path directory;

    private DefaultDesignerService designerService() {
        return new DefaultDesignerService(TestSettings.inDirectory(directory), new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    private static List<String> names(DefaultDesignerService designerService) {
        return designerService.getAllProjects().stream().map(Project::getName).toList();
    }

    @Test
    void loadsTheOtherProjectsBesideABrokenOne() throws Exception {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        Path designer = Files.createDirectories(directory.resolve("designer"));
        Files.writeString(designer.resolve("Show.json"), "{\"name\": \"Show\", \"version\": 7}");
        // Damaged, left while writing, copied from a Mac
        Files.writeString(designer.resolve("Broken.json"), "{\"name\": \"Broken\", \"scenes\": [");
        Files.writeString(designer.resolve(".Show.json.tmp"), "{");
        Files.writeString(designer.resolve("._Show.json"), "binary");

        // The app starts with the designer service
        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());

        assertEquals(List.of("Show"), designerService.getAllProjects().stream().map(Project::getName).toList());
    }

    @Test
    void writesNoHalfProjects() throws Exception {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());

        designerService.saveProject("{\"name\": \"Show\", \"version\": 7}");

        try (var files = Files.list(directory.resolve("designer"))) {
            assertEquals(List.of("Show.json"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void findsAProjectByItsName() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.saveProject("{\"name\": \"Show\", \"version\": 10}");
        designerService.saveProject("{\"name\": \"Rehearsal\", \"version\": 10}");

        assertEquals("Rehearsal", designerService.getProjectByName("Rehearsal").getName());
        assertNull(designerService.getProjectByName("Tour"));
    }

    @Test
    void replacesAProjectSavedAgain() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.saveProject("{\"name\": \"Show\", \"version\": 10, \"masterDimmerValue\": 1}");
        designerService.saveProject("{\"name\": \"Rehearsal\", \"version\": 10}");

        designerService.saveProject("{\"name\": \"Show\", \"version\": 10, \"masterDimmerValue\": 0.5}");

        assertEquals(List.of("Rehearsal", "Show"), names(designerService));
        assertEquals(0.5, designerService.getProjectByName("Show").getMasterDimmerValue());
    }

    @Test
    void deletesOnlyTheProjectOfThatName() throws Throwable {
        DefaultDesignerService designerService = designerService();
        designerService.saveProject("{\"name\": \"Show\", \"version\": 10}");
        designerService.saveProject("{\"name\": \"Rehearsal\", \"version\": 10}");

        List<String> errors = DesignerTestEngine.logged(Level.ERROR, () -> designerService.deleteProjectByName("Show"));

        assertEquals(List.of(), errors);
        assertEquals(List.of("Rehearsal"), names(designerService));
        assertFalse(Files.exists(directory.resolve("designer/Show.json")));
        assertTrue(Files.exists(directory.resolve("designer/Rehearsal.json")));
    }

    private static List<String> compositionNames(JsonNode project) {
        List<String> names = new ArrayList<>();
        project.get("compositions").forEach(composition -> names.add(composition.get("name").asString()));
        return names;
    }

    @Test
    void renamesACompositionInTheProjectsPlayingAlongWithIt() throws Exception {
        DefaultDesignerService designerService = designerService();
        // with a part the app doesn't read, which stays as it is
        designerService.saveProject("""
                {"name": "Show", "version": 10, "compositions": [{"uuid": "song", "name": "Song"}, {"uuid": "other", "name": "Other"}],
                 "fixtureFolders": [{"uuid": "folder", "name": "Front truss"}]}
                """);
        designerService.saveProject("{\"name\": \"Rehearsal\", \"version\": 10, \"compositions\": [{\"uuid\": \"other\", \"name\": \"Other\"}]}");
        Path rehearsal = directory.resolve("designer/Rehearsal.json");
        String rehearsalBefore = Files.readString(rehearsal);

        designerService.renameComposition("Song", "Hit");

        JsonNode show = new ObjectMapper().readTree(directory.resolve("designer/Show.json").toFile());
        assertEquals(List.of("Hit", "Other"), compositionNames(show));
        assertEquals("Front truss", show.get("fixtureFolders").get(0).get("name").asString());
        assertEquals("Show", designerService.getProjectByCompositionName("Hit").getName());
        assertNull(designerService.getProjectByCompositionName("Song"));

        // a project without the composition isn't written again
        assertEquals(rehearsalBefore, Files.readString(rehearsal));
    }

}
