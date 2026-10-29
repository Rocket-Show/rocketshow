package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.settings.DefaultSettingsService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.update.UpdateState;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Writes the sample files of a release into src/test/resources/compat/&lt;release&gt;/, the way the release saves
 * them: settings, session, update state, a composition, a set and a designer project, each using every feature (see
 * {@link SampleFiller}). Run it once for each release and commit the folder. The tests of every later release load
 * these files ({@link CompatibilityTest} and others).
 * <pre>
 * ./mvnw test -Dskip.npm -Dskip.installnodenpm -Dtest=ReleaseSampleGenerator -Dsample.release=3.0.1
 * </pre>
 * Not run with the other tests (the class name doesn't end with Test).
 */
class ReleaseSampleGenerator {

    private final static String COMPOSITION_NAME = "Sample composition";

    @Test
    void generate() throws Exception {
        String release = System.getProperty("sample.release");
        assertNotNull(release, "Pass the release, e.g. -Dsample.release=3.0.1");

        // The samples of a release never change
        Path releasePath = Path.of("src", "test", "resources", "compat", release);
        assertFalse(Files.exists(releasePath), releasePath + " exists already");

        write(Settings.class, getSettings(), releasePath.resolve("settings.xml"));
        write(Session.class, new SampleFiller().fill(new Session()), releasePath.resolve("session.xml"));
        write(UpdateState.class, new SampleFiller().fill(new UpdateState()), releasePath.resolve("update.xml"));

        Composition composition = new SampleFiller().fill(new Composition());
        composition.setName(COMPOSITION_NAME);
        write(Composition.class, composition, releasePath.resolve("compositions").resolve(COMPOSITION_NAME + ".xml"));

        Set set = new SampleFiller().fill(new Set());
        set.setName("Sample set");
        write(Set.class, set, releasePath.resolve("sets").resolve("Sample set.xml"));

        Path designerPath = releasePath.resolve("designer");
        Files.createDirectories(designerPath);
        Files.writeString(designerPath.resolve("Sample project.json"), getDesignerProject());
    }

    private Settings getSettings() throws Exception {
        Settings settings = new SampleFiller().fill(new Settings());

        // A unit of this release loads its settings without migrating them. Keep the default log level, otherwise
        // the tests loading the settings change it.
        settings.setVersion(DefaultSettingsService.CURRENT_SETTINGS_VERSION);
        settings.setLoggingLevel(Settings.LoggingLevel.INFO);

        return settings;
    }

    // Write like the services of the app do
    private void write(Class<?> type, Object object, Path file) throws Exception {
        Files.createDirectories(file.getParent());

        Marshaller marshaller = JAXBContext.newInstance(type).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(object, file.toFile());
    }

    // Designer projects are written by the designer in the web app, not by the models here. Take the designer
    // template of this release and use more of its features: steps, fades, several presets, scenes and compositions.
    private String getDesignerProject() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode project = (ObjectNode) mapper.readTree(Path.of("dist", "designer_template.json").toFile());
        project.put("name", "Sample project");

        // A preset fading in and out, looping two steps
        ObjectNode preset = (ObjectNode) project.get("presets").get(0);
        preset.put("fadeInMillis", 500);
        preset.put("fadeOutMillis", 800);
        preset.put("stepsLoop", true);
        preset.put("stepsLoopMillis", 8000);

        ArrayNode steps = (ArrayNode) preset.get("steps");
        ObjectNode secondStep = (ObjectNode) steps.get(0).deepCopy();
        secondStep.put("uuid", "sample-step-2");
        secondStep.put("startMillis", 4000);
        secondStep.put("transitionMillis", 1500);
        for (JsonNode value : secondStep.get("fixtureCapabilityValues")) {
            ((ObjectNode) value).put("valuePercentage", 0.25);
        }
        steps.add(secondStep);

        // A second preset in a second scene, played after the first one
        ObjectNode secondPreset = preset.deepCopy();
        secondPreset.put("uuid", "sample-preset-2");
        secondPreset.put("name", "Second preset");
        for (JsonNode step : secondPreset.get("steps")) {
            ((ObjectNode) step).put("uuid", "sample-preset-2-" + step.get("uuid").asString());
        }
        ((ArrayNode) project.get("presets")).add(secondPreset);

        ObjectNode secondScene = (ObjectNode) project.get("scenes").get(0).deepCopy();
        secondScene.put("uuid", "sample-scene-2");
        secondScene.put("name", "Second scene");
        secondScene.putArray("presetUuids").add("sample-preset-2");
        ((ArrayNode) project.get("scenes")).add(secondScene);

        ObjectNode composition = (ObjectNode) project.get("compositions").get(0);
        ObjectNode secondRegion = (ObjectNode) composition.get("scenePlaybackRegions").get(0).deepCopy();
        secondRegion.put("sceneUuid", "sample-scene-2");
        secondRegion.put("startMillis", 90000);
        secondRegion.put("endMillis", 120000);
        ((ArrayNode) composition.get("scenePlaybackRegions")).add(secondRegion);

        // A second composition with both scenes
        ObjectNode secondComposition = composition.deepCopy();
        secondComposition.put("uuid", "sample-composition-2");
        secondComposition.put("name", COMPOSITION_NAME);
        ((ArrayNode) project.get("compositions")).add(secondComposition);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(project);
    }

}
