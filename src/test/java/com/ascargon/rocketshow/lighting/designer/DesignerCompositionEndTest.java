package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.DefaultFileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Where the lights of a composition of the designer have gone out: a composition plays at least that long, also one
 * without files or actions. These mirror the specs of the timeline in the Rocket Show Designer, which plays a
 * composition without an audio file that long.
 */
class DesignerCompositionEndTest {

    @TempDir
    Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode project;
    private DefaultDesignerService designerService;

    @BeforeEach
    void setUp() {
        project = objectMapper.createObjectNode();
        project.put("version", 9);
        project.put("name", "Show");
        project.putArray("presets");
        project.putArray("scenes");
        ObjectNode composition = project.putArray("compositions").addObject();
        composition.put("uuid", "song");
        composition.put("name", "Song");
        composition.put("syncType", "none");
        composition.putArray("scenePlaybackRegions");

        designerService = new DefaultDesignerService(TestSettings.inDirectory(directory), new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    // a scene playing one preset
    private ObjectNode addScene(String uuid) {
        ObjectNode preset = ((ArrayNode) project.get("presets")).addObject();
        preset.put("uuid", uuid + "-preset");
        preset.put("name", uuid);

        ObjectNode scene = ((ArrayNode) project.get("scenes")).addObject();
        scene.put("uuid", uuid);
        scene.put("name", uuid);
        scene.putArray("presetUuids").add(uuid + "-preset");

        return scene;
    }

    private ObjectNode preset(String sceneUuid) {
        for (var preset : project.get("presets")) {
            if (preset.get("uuid").asString().equals(sceneUuid + "-preset")) {
                return (ObjectNode) preset;
            }
        }

        throw new IllegalArgumentException(sceneUuid);
    }

    private void play(String sceneUuid, long startMillis, long endMillis) {
        ObjectNode region = ((ArrayNode) project.get("compositions").get(0).get("scenePlaybackRegions")).addObject();
        region.put("sceneUuid", sceneUuid);
        region.put("startMillis", startMillis);
        region.put("endMillis", endMillis);
    }

    // saved from the designer, like it does it
    private long endOfSong() throws Exception {
        designerService.saveProject(objectMapper.writeValueAsString(project));

        return designerService.getCompositionEndMillis("Song");
    }

    @Test
    void endsWithItsLastClip() throws Exception {
        addScene("a");
        play("a", 20000, 30000);
        play("a", 0, 10000);

        assertEquals(30000, endOfSong());
    }

    @Test
    void hasNoLengthWithoutAProject() {
        assertEquals(0, designerService.getCompositionEndMillis("Song"));
    }

    @Test
    void hasNoLengthWithoutClips() throws Exception {
        addScene("a");

        assertEquals(0, endOfSong());
    }

    @Test
    void leavesAClipWithoutItsSceneOut() throws Exception {
        addScene("a");
        play("a", 0, 10000);
        play("deleted", 20000, 30000);

        assertEquals(10000, endOfSong());
    }

    @Test
    void waitsForTheSceneToFadeOutPastItsClip() throws Exception {
        ObjectNode scene = addScene("a");
        scene.put("fadeOutMillis", 2000);
        scene.put("fadeOutPost", true);
        play("a", 0, 10000);

        assertEquals(12000, endOfSong());
    }

    @Test
    void waitsForAPresetFadingOutPastItsEnd() throws Exception {
        addScene("a");
        preset("a").put("fadeOutMillis", 3000);
        preset("a").put("fadeOutPost", true);
        play("a", 0, 10000);

        assertEquals(13000, endOfSong());
    }

    @Test
    void followsAPresetPlayingLongerThanItsClip() throws Exception {
        addScene("a");
        preset("a").put("endMillis", 15000);
        play("a", 5000, 10000);

        assertEquals(20000, endOfSong());
    }

}
