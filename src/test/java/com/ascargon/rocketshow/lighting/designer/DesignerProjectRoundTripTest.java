package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A project of the designer, read by Rocket Show and written again (as the projects are handed out, e.g. GET projects),
 * keeps everything the designer saved: what the live preview of the designer sends, the timeline, the presets and their
 * effects, down to the fixture capabilities and the pictures of the pixel effects.
 */
class DesignerProjectRoundTripTest {

    // everything set to a value which is not its default
    private static final String PROJECT = """
            {
              "version": 10,
              "uuid": "project-1",
              "name": "Round trip",
              "selectedPresetUuid": "preset-1",
              "selectedStepUuid": "step-1",
              "stepPreviewRunning": true,
              "stepPreviewStartMillis": 1234.5,
              "selectedSceneUuids": ["scene-1", "scene-2"],
              "identifyFixtureUuids": ["fixture-3"],
              "resetFixtureUuids": ["fixture-2"],
              "lampOnFixtureUuids": ["fixture-1"],
              "dmxOverrides": [{"universeUuid": "universe-1", "dmxChannel": 7, "value": 200}],
              "previewHolds": [{"positionMillis": 60000, "heldMillis": 2500}],
              "previewHolding": true,
              "previewPreset": true,
              "compositions": [{
                "uuid": "composition-1",
                "name": "Song",
                "durationMillis": 180000,
                "beatsPerMinute": 128,
                "timeSignatureUpper": 3,
                "markers": [
                  {"uuid": "marker-1", "name": "Bridge", "positionMillis": 60000, "type": "hold"},
                  {"uuid": "marker-2", "name": "Outro", "positionMillis": 150000, "type": "label"}
                ]
              }],
              "presets": [{
                "uuid": "preset-1",
                "name": "Wash",
                "mirrorPan": true,
                "mirrorTilt": true,
                "patternGroupSize": 2,
                "patternWings": 3,
                "patternEvery": 2,
                "effects": [
                  {
                    "type": "curve",
                    "uuid": "curve-1",
                    "lengthMode": "beats",
                    "lengthBeats": 8,
                    "phaseMillis": 100,
                    "phaseBeats": 0.5,
                    "phasingMode": "beats",
                    "phasingMillis": 50,
                    "phasingBeats": 0.25,
                    "phasingCycles": 2,
                    "phasingGroupSize": 3,
                    "runMode": "cycles",
                    "runCycles": 4,
                    "runDurationMillis": 9000,
                    "endMode": "reset",
                    "panTiltInDegrees": true,
                    "capabilities": [{
                      "type": "Zoom",
                      "distanceStart": "1m",
                      "distanceEnd": "10m",
                      "frostIntensityStart": "0%",
                      "frostIntensityEnd": "100%",
                      "duration": "2s",
                      "insertion": "in",
                      "shakeSpeed": "fast",
                      "hold": "3s"
                    }]
                  },
                  {"type": "pan-tilt", "uuid": "pan-tilt-1", "shape": "figure8", "sizePanDegrees": 90, "sizeTiltDegrees": 45},
                  {
                    "type": "pixel",
                    "uuid": "pixel-1",
                    "pattern": "text",
                    "bitmap": {"width": 2, "height": 1, "channels": 1, "frames": ["AP8="], "durations": [250], "letterStarts": [0]}
                  }
                ]
              }]
            }
            """;

    // the switches of the project, its presets and their effects off
    private static final String SWITCHED_OFF = """
            {
              "version": 10,
              "stepPreviewRunning": false,
              "previewHolding": false,
              "previewPreset": false,
              "presets": [{
                "uuid": "preset-1",
                "mirrorPan": false,
                "mirrorTilt": false,
                "effects": [{"type": "curve", "uuid": "curve-1", "panTiltInDegrees": false}]
              }]
            }
            """;

    private static JsonNode roundTrip(String json) {
        Project project = DesignerJson.MAPPER.readValue(json, Project.class);
        return DesignerJson.MAPPER.readTree(DesignerJson.MAPPER.writeValueAsString(project));
    }

    // every value of the expected JSON is in the actual one, which may have more (the defaults of what isn't set)
    private static void assertKept(String path, JsonNode expected, JsonNode actual) {
        if (actual == null || actual.isNull()) {
            fail(path + " is missing");
        }

        if (expected.isObject()) {
            for (Map.Entry<String, JsonNode> field : expected.properties()) {
                assertKept(path + "." + field.getKey(), field.getValue(), actual.get(field.getKey()));
            }
        } else if (expected.isArray()) {
            assertEquals(expected.size(), actual.size(), path + " has another length");
            Iterator<JsonNode> actualElements = actual.iterator();
            int index = 0;
            for (JsonNode element : expected) {
                assertKept(path + "[" + index++ + "]", element, actualElements.next());
            }
        } else if (expected.isNumber()) {
            assertEquals(expected.asDouble(), actual.asDouble(), 1e-9, path);
        } else {
            assertEquals(expected.asString(), actual.asString(), path);
        }
    }

    @Test
    void keepsWhatTheDesignerSaved() {
        assertKept("project", DesignerJson.MAPPER.readTree(PROJECT), roundTrip(PROJECT));
    }

    @Test
    void keepsWhatIsSwitchedOff() {
        assertKept("project", DesignerJson.MAPPER.readTree(SWITCHED_OFF), roundTrip(SWITCHED_OFF));
    }

    @Test
    void tellsTheHoldPointsFromTheLabels() {
        Project project = DesignerJson.MAPPER.readValue(PROJECT, Project.class);
        List<CompositionMarker> markers = project.getCompositions()[0].getMarkers();

        assertTrue(markers.get(0).isHold());
        assertFalse(markers.get(1).isHold());
    }

    @Test
    void keepsTheTimeSignatureAboveZero() {
        Composition composition = new Composition();

        composition.setTimeSignatureUpper(1);
        assertEquals(1, composition.getTimeSignatureUpper());

        // a time signature the designer couldn't work out is 4/4
        composition.setTimeSignatureUpper(0);
        assertEquals(4, composition.getTimeSignatureUpper());
        composition.setTimeSignatureUpper(-3);
        assertEquals(4, composition.getTimeSignatureUpper());

        composition.setMarkers(null);
        assertTrue(composition.getMarkers().isEmpty());
    }

}
