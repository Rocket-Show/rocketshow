package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.engine.LightingEngine;
import com.ascargon.rocketshow.lighting.engine.LightingEngineConformanceTest;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A project handed to the shared lighting engine the way Rocket Show hands it over (read into its model, migrated,
 * written with DesignerProjectJson) plays the same as the JSON of the designer: the bits of every value of every frame
 * of the channelValues cases come to the digests the engine recorded from its native build (see
 * LightingEngineConformanceTest, which plays the JSON of the cases as it is).
 * <p>
 * The test playing them is skipped without engine.wasm (see testing/build-lighting-engine.sh).
 */
class DesignerProjectJsonTest {

    private static final Path CONFORMANCE = Path.of("src/test/resources/designer-conformance");
    private static final Path DIGESTS = Path.of("src/test/resources/lighting-engine/core-digests.json");

    @TempDir
    Path directory;

    private DefaultDesignerService designerService() {
        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerLivePreview(false);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class));
    }

    @Test
    void writesTheProfilesAsTheDesignerDoes() {
        Project project = DesignerJson.MAPPER.readValue("""
                {"version": 11, "fixtureProfiles": [{"uuid": "p", "name": "Par",
                  "availableChannels": {"Dimmer": {"capability": {"type": "Intensity"}}},
                  "templateChannels": {"Red $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Red"}}},
                  "wheels": {"Color Wheel": {"slots": [{"type": "Open"}]}},
                  "modes": [{"name": "Mode", "shortName": "m", "channels": ["Dimmer", null,
                    {"insert": "matrixChannels", "repeatFor": ["eachPixelXYZ"], "channelOrder": "perPixel", "templateChannels": ["Red $pixelKey"]}]}]}]}
                """, Project.class);

        JsonNode profile = DesignerJson.MAPPER.readTree(DesignerProjectJson.write(project)).get("fixtureProfiles").get(0);

        assertEquals("Intensity", profile.get("availableChannels").get("Dimmer").get("capability").get("type").asString());
        assertEquals("Red", profile.get("templateChannels").get("Red $pixelKey").get("capability").get("color").asString());
        assertEquals("Open", profile.get("wheels").get("Color Wheel").get("slots").get(0).get("type").asString());
        assertEquals("Dimmer", profile.get("modes").get(0).get("channels").get(0).asString());
        assertEquals(true, profile.get("modes").get(0).get("channels").get(1).isNull());
        assertEquals("matrixChannels", profile.get("modes").get(0).get("channels").get(2).get("insert").asString());
        assertEquals("eachPixelXYZ", profile.get("modes").get(0).get("channels").get(2).get("repeatFor").get(0).asString());
        assertEquals("Red $pixelKey", profile.get("modes").get(0).get("channels").get(2).get("templateChannels").get(0).asString());
    }

    @TestFactory
    Stream<DynamicTest> handsTheProjectsOverToTheSharedEngineAsTheDesignerWritesThem() throws IOException {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build, see testing/build-lighting-engine.sh");
        LightingEngineConformanceTest.loadTheEngine();

        DefaultDesignerService designerService = designerService();
        JsonNode recorded = DesignerJson.MAPPER.readTree(DIGESTS.toFile());
        List<DynamicTest> tests = new ArrayList<>();

        for (Map.Entry<String, JsonNode> file : recorded.properties()) {
            tests.add(DynamicTest.dynamicTest(file.getKey(), () -> assertEquals(file.getValue().asString(),
                    LightingEngineConformanceTest.digestOf(CONFORMANCE.resolve("cases").resolve(file.getKey()), projectNode -> {
                        try {
                            Project project = DesignerJson.MAPPER.readValue(LightingEngineConformanceTest.resolveProfiles(projectNode), Project.class);
                            ProjectMigration.migrate(project);
                            return DesignerProjectJson.write(project);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }))));
        }

        return tests.stream();
    }

}
