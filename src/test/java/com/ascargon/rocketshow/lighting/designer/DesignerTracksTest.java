package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tracks of a composition of the designer: the scenes on the track higher up are on top of the ones below. Hiding
 * and soloing tracks helps working on the show in the designer: they shape its preview, the show plays every track.
 * These mirror the specs of the timeline in the Rocket Show Designer, so a show looks the same on stage as it did
 * while it was written.
 */
class DesignerTracksTest {

    private static final String UNIVERSE = "universe";

    @TempDir
    Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // A dimmer at the first DMX address. Two scenes play over the whole song: "dim" puts it at 20 %, "bright" at
    // 100 %. "dim" comes first in the project, so it was on top while the scenes decided the layers.
    private ObjectNode project(int version) {
        ObjectNode project = objectMapper.createObjectNode();
        project.put("version", version);
        project.put("name", "Tracks");
        project.put("masterDimmerValue", 1);

        ObjectNode profile = project.putArray("fixtureProfiles").addObject();
        profile.put("uuid", "test/dimmer");
        profile.put("name", "Dimmer");
        ObjectNode dimmer = profile.putObject("availableChannels").putObject("Dimmer");
        dimmer.putArray("fineChannelAliases");
        dimmer.putObject("capability").put("type", "Intensity");
        ObjectNode mode = profile.putArray("modes").addObject();
        mode.put("name", "1-channel");
        mode.put("shortName", "1ch");
        ObjectNode channel = mode.putArray("channels").addObject();
        channel.put("name", "Dimmer");
        channel.putArray("repeatFor");
        channel.putArray("templateChannels");

        ObjectNode fixture = project.putArray("fixtures").addObject();
        fixture.put("uuid", "dimmer");
        fixture.put("name", "Dimmer");
        fixture.put("profileUuid", "test/dimmer");
        fixture.put("modeShortName", "1ch");
        fixture.put("dmxUniverseUuid", UNIVERSE);
        fixture.put("dmxFirstChannel", 0);
        project.putArray("presetFixtures").addObject().put("fixtureUuid", "dimmer");

        project.putArray("presets");
        project.putArray("scenes");
        addScene(project, "dim", 0.2);
        addScene(project, "bright", 1.0);

        ObjectNode composition = project.putArray("compositions").addObject();
        composition.put("uuid", "song");
        composition.put("name", "Song");
        composition.putArray("scenePlaybackRegions");

        return project;
    }

    // a scene playing one preset, which sets the dimmer
    private void addScene(ObjectNode project, String name, double intensity) {
        ObjectNode preset = ((ArrayNode) project.get("presets")).addObject();
        preset.put("uuid", name);
        preset.put("name", name);
        preset.putArray("fixtures").addObject().put("fixtureUuid", "dimmer");
        preset.putArray("fixtureCapabilityValues");
        preset.putArray("fixtureChannelValues");
        preset.putArray("effects");

        ObjectNode step = preset.putArray("steps").addObject();
        step.put("uuid", name + "-step");
        step.put("startMillis", 0);
        ObjectNode value = step.putArray("fixtureCapabilityValues").addObject();
        value.put("type", "Intensity");
        value.put("valuePercentage", intensity);
        step.putArray("fixtureChannelValues");
        step.putArray("effectAmounts");

        ObjectNode scene = ((ArrayNode) project.get("scenes")).addObject();
        scene.put("uuid", name + "-scene");
        scene.put("name", name);
        scene.putArray("presetUuids").add(name);
        scene.put("fadeInMillis", 0);
        scene.put("fadeOutMillis", 0);
    }

    private static ObjectNode composition(ObjectNode project) {
        return (ObjectNode) project.get("compositions").get(0);
    }

    private static ObjectNode addTrack(ObjectNode project, String uuid) {
        ObjectNode composition = composition(project);
        ArrayNode tracks = composition.has("tracks") ? (ArrayNode) composition.get("tracks") : composition.putArray("tracks");
        ObjectNode track = tracks.addObject();
        track.put("uuid", uuid);
        track.put("name", uuid);

        return track;
    }

    // play the scene over the whole song on the passed track (none in a project from before the tracks)
    private static void play(ObjectNode project, String sceneName, String trackUuid) {
        ObjectNode region = ((ArrayNode) composition(project).get("scenePlaybackRegions")).addObject();
        region.put("sceneUuid", sceneName + "-scene");
        region.put("startMillis", 0);
        region.put("endMillis", 60000);
        if (trackUuid != null) {
            region.put("trackUuid", trackUuid);
        }
    }

    // "bright" on the top track, "dim" on the one below
    private ObjectNode brightOnTop() {
        ObjectNode project = project(10);
        addTrack(project, "top");
        addTrack(project, "bottom");
        play(project, "dim", "bottom");
        play(project, "bright", "top");

        return project;
    }

    // the value of the dimmer, while the show plays
    private int show(ObjectNode project) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        int dimmer = engine.play(directory, project.toString(), "Song", 5000, List.of(UNIVERSE)).get(UNIVERSE).getOrDefault(1, 0);

        assertEquals(List.of(), engine.errors);
        return dimmer;
    }

    // the value of the dimmer, while the designer previews the song
    private int preview(ObjectNode project) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        int dimmer = engine.preview(directory, project.toString(), designerService -> designerService.setPreviewComposition("Song"), List.of(UNIVERSE))
                .get(UNIVERSE).getOrDefault(1, 0);

        assertEquals(List.of(), engine.errors);
        return dimmer;
    }

    @Test
    void playsTheTopTrackOverTheOnesBelow() throws Exception {
        assertEquals(255, show(brightOnTop()));
        assertEquals(255, preview(brightOnTop()));
    }

    @Test
    void followsTheTracksWhenTheyAreMoved() throws Exception {
        ObjectNode project = project(10);
        addTrack(project, "top");
        addTrack(project, "bottom");
        play(project, "dim", "top");
        play(project, "bright", "bottom");

        assertEquals(51, show(project));
    }

    @Test
    void playsAProjectFromBeforeTheTracksLikeBefore() throws Exception {
        // the first scene of the project was on top: it gets the top track
        ObjectNode project = project(9);
        play(project, "bright", null);
        play(project, "dim", null);

        assertEquals(51, show(project));
    }

    @Test
    void leavesAHiddenTrackOutOfThePreviewOnly() throws Exception {
        ObjectNode project = brightOnTop();
        ((ObjectNode) composition(project).get("tracks").get(0)).put("hidden", true);

        assertEquals(51, preview(project));
        assertEquals(255, show(project));
    }

    @Test
    void previewsOnlyTheSoloedTracks() throws Exception {
        ObjectNode project = brightOnTop();
        ((ObjectNode) composition(project).get("tracks").get(1)).put("solo", true);

        assertEquals(51, preview(project));
        assertEquals(255, show(project));
    }

    // A scene fading in before its clip or out past it plays meanwhile: its presets play as long as the scene
    @Test
    void playsASceneWhileItFadesOutsideItsClip() throws Exception {
        ObjectNode project = project(10);
        addTrack(project, "top");
        ObjectNode region = ((ArrayNode) composition(project).get("scenePlaybackRegions")).addObject();
        region.put("sceneUuid", "bright-scene");
        region.put("trackUuid", "top");
        region.put("startMillis", 10000);
        region.put("endMillis", 20000);
        ObjectNode scene = (ObjectNode) project.get("scenes").get(1);
        scene.put("fadeInMillis", 2000);
        scene.put("fadeInPre", true);
        scene.put("fadeOutMillis", 2000);
        scene.put("fadeOutPost", true);

        // half way into the fades, half of the scene is there
        assertEquals(128, dimmerAt(project, 9000), 1);
        assertEquals(128, dimmerAt(project, 21000), 1);
        assertEquals(0, dimmerAt(project, 22500));
    }

    private int dimmerAt(ObjectNode project, long positionMillis) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        int dimmer = engine.play(directory, project.toString(), "Song", positionMillis, List.of(UNIVERSE)).get(UNIVERSE).getOrDefault(1, 0);

        assertEquals(List.of(), engine.errors);
        return dimmer;
    }

    @Test
    void playsNothingOfACompositionWithoutTracks() throws Exception {
        // e.g. one which only plays actions
        assertEquals(0, show(project(10)));
    }

    @Test
    void putsEveryScenePlayedBeforeTheTracksOnATrackOfItsOwn() {
        Scene first = new Scene();
        first.setUuid("first");
        first.setName("First");
        Scene second = new Scene();
        second.setUuid("second");
        second.setName("Second");

        Composition composition = new Composition();
        composition.setScenePlaybackRegions(new ScenePlaybackRegion[]{
                region("second", 0, 4000), region("first", 0, 4000), region("first", 2000, 6000), region("second", 5000, 8000)
        });

        DefaultDesignerService.assignTracks(composition, List.of(first, second));

        // the clips of the first scene which overlap each other are on two tracks, right below each other
        assertEquals(List.of("First", "First 2", "Second"), composition.getTracks().stream().map(Track::getName).toList());
        ScenePlaybackRegion[] regions = composition.getScenePlaybackRegions();
        List<Track> tracks = composition.getTracks();
        assertEquals(tracks.get(2).getUuid(), regions[0].getTrackUuid());
        assertEquals(tracks.get(0).getUuid(), regions[1].getTrackUuid());
        assertEquals(tracks.get(1).getUuid(), regions[2].getTrackUuid());
        assertEquals(tracks.get(2).getUuid(), regions[3].getTrackUuid());
    }

    @Test
    void leavesACompositionWithoutClipsWithoutTracks() {
        Composition composition = new Composition();
        composition.setScenePlaybackRegions(new ScenePlaybackRegion[0]);

        DefaultDesignerService.assignTracks(composition, List.of(new Scene()));

        assertEquals(List.of(), composition.getTracks());
    }

    private static ScenePlaybackRegion region(String sceneUuid, long startMillis, long endMillis) {
        ScenePlaybackRegion region = new ScenePlaybackRegion();
        region.setSceneUuid(sceneUuid);
        region.setStartMillis(startMillis);
        region.setEndMillis(endMillis);

        return region;
    }

}
