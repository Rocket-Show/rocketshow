package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.util.AtomicFile;
import com.ascargon.rocketshow.util.FileFilterService;
import com.ascargon.rocketshow.util.FileNames;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ascargon.rocketshow.audio.AudioAnalysisService;
import com.ascargon.rocketshow.audio.AudioBus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Service
public class DefaultDesignerService implements DesignerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDesignerService.class);

    private final String CHANNEL_ORDER_PER_PIXEL = "perPixel";
    private final String CHANNEL_ORDER_PER_CHANNEL = "perChannel";

    // the dimmer a fixture lighting in colors without one gets, see addVirtualDimmers()
    static final String VIRTUAL_DIMMER_CHANNEL_NAME = "$virtualDimmer";

    private final SettingsService settingsService;
    private final FileFilterService fileFilterService;
    private final LightingService lightingService;
    private final FixturePixelGroupResolveService fixturePixelGroupResolveService;

    private List<Project> projects;

    // The currently played project, pipeline and player
    private Project project;
    private Pipeline pipeline;
    private CompositionPlayer compositionPlayer;
    private Composition composition;
    private long startPositionMillis = 0;

    // the highest project version this player knows. A project written by a newer
    // designer still plays as well as it can, but whatever came after this version is
    // missing from it. Package visible for the test of the designer template.
    static final int SUPPORTED_PROJECT_VERSION = 11;

    // Live preview
    private boolean playPreview = false; // Is the preview playing a composition?
    private boolean previewPreset = false;
    private String selectedPresetUuid;
    private String selectedStepUuid;
    private boolean stepPreviewRunning = false;
    private double stepPreviewStartMillis = 0;
    private List<String> selectedSceneUuids = new ArrayList<>();

    // the holds the timeline of the composition playing went through (see CompositionHold): the clips spanning them ran
    // on while the position stood still
    private volatile List<CompositionHold> holds = new ArrayList<>();

    // the fixtures being identified from the fixture pool of the designer (see Identify)
    private volatile List<String> identifyFixtureUuids = new ArrayList<>();

    // the DMX channels the faders of the designer hold at a value while it previews (see setDmxOverrides)
    private volatile List<DmxOverride> dmxOverrides = new ArrayList<>();

    // the fixtures the designer asks from its fixture pool to reset themselves or to switch their lamp on (see
    // Maintenance)
    private volatile List<String> resetFixtureUuids = new ArrayList<>();
    private volatile List<String> lampOnFixtureUuids = new ArrayList<>();

    // The scenes picked by hand on the Play screen (see ScenePlayerService), the project they are from and the master
    // dimmer of the Play screen. They play whenever no composition plays a project of its own: right away, or once the
    // composition closes its project.
    // (read by the timer sending the universes and by the players of the compositions)
    private Project manualScenesProject;
    private List<String> manualSceneUuids = new ArrayList<>();
    private volatile double manualScenesMasterDimmer = 1;
    private volatile boolean playingManualScenes = false;

    // The beats of the tempo playing (see BeatClock): the one from outside (tapped, set or of a MIDI clock, see
    // TempoService), else the programmed one of the composition or the project playing
    private final BeatClock beatClock = new BeatClock(System.currentTimeMillis());
    private volatile Double outsideBeatsPerMinute;

    // the beats a clip of a composition following the tempo from outside started on, by its region (see
    // getBeatTimeMillis), and the beats of the tempo at the moment worked out
    private final Map<ScenePlaybackRegion, Double> regionStartBeats = new IdentityHashMap<>();
    private double frameBeats = 0;

    // the timeline of the composition playing holds at a hold point until GO: since when, in real time (see hold)
    private volatile boolean holding = false;
    private double holdStartRealMillis = 0;

    // the scenes picked by hand and flashed while they play: their fades, their own clocks and the live controls of the
    // Play screen (see LiveScenes)
    private final LiveScenes liveScenes = new LiveScenes(beatClock);
    private List<String> flashedSceneUuids = new ArrayList<>();

    // the scenes picked by hand were let go: they play until they have faded out, then their lights are turned off
    private volatile boolean releasingManualScenes = false;

    private List<LightingUniverseState> lightingUniverses = new ArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> universeSenderHandle;

    // Held while the timer sends a universe, so stopping the timer waits for a send running at the moment
    private final Object universeSendLock = new Object();

    private List<CachedFixture> cachedFixtures;

    // a fixture or one of its pixels. Like in the designer, an empty pixel key is no pixel key.
    private record PresetFixtureKey(String fixtureUuid, String pixelKey) {
        PresetFixtureKey {
            pixelKey = emptyToNull(pixelKey);
        }
    }

    // the place of each fixture of a preset in its chase (see calculatePresetChase) and the number of its steps
    // the place of each fixture (or pixel) in the chase of a preset and the number of its steps, and the same for the
    // fixtures with all of their pixels (by fixture uuid), which the pixel effects chase over
    private record PresetChase(Map<PresetFixtureKey, Integer> fixtureIndices, int fixtureCount, Map<String, Integer> wholeFixtureIndices, int wholeFixtureCount) {
    }

    // the chases of the presets of the loaded project, by preset (presets don't override equals)
    private Map<Preset, PresetChase> presetChases = new ConcurrentHashMap<>();

    // the places of the parts of a preset in the picture of the stage they make up, by preset and view, for the pixel
    // effects spread across the stage (see PixelStage)
    private Map<Preset, Map<String, Map<CachedFixture, PixelContext>>> stagePixelPlaces = new ConcurrentHashMap<>();

    // the places of the parts of a preset in its fan, by preset and order, for a preset fanning by where they are on
    // the stage (see Fan)
    private Map<Preset, Map<String, Map<CachedFixture, Double>>> stageFanPlaces = new ConcurrentHashMap<>();

    // For each cached fixture, the one before it on the same DMX address (the same lamp, which it takes the values of),
    // or -1. Worked out once: looking it up in each frame took longer than the frame with a few thousand pixels.
    private int[] sameLampIndices = new int[0];

    // Where a part of a fixture sends its channels: the DMX address of each channel of its mode which is one of its own,
    // the channel and which of its bytes it is (0 for the coarse one, then the fine ones). Worked out once for each part:
    // going through the whole mode for each part in each frame took the most time of a frame of a matrix.
    private record OutputChannel(int address, CachedFixtureChannel channel, int fineIndex) {
    }

    private Map<CachedFixture, List<OutputChannel>> outputChannels = new ConcurrentHashMap<>();

    private long lastPlayTimeMillis;

    // the analysis of the audio of the composition playing, which the pixel effects follow, and what a frame knows of
    // it while it is worked out: its moment, the audio of each effect following it and the settings of the ones
    // following it with their size
    private final AudioAnalysisService audioAnalysisService;
    private double frameTimeMillis;
    private final Map<EffectPixel, PixelAudio> frameAudio = new HashMap<>();
    private final Map<EffectPixel, PixelPatternSettings> frameSizedSettings = new HashMap<>();

    public DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService,
            FixturePixelGroupResolveService fixturePixelGroupResolveService
    ) {
        this(settingsService, fileFilterService, lightingService, fixturePixelGroupResolveService, new AudioAnalysisService());
    }

    @Autowired
    public DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService,
            FixturePixelGroupResolveService fixturePixelGroupResolveService,
            AudioAnalysisService audioAnalysisService
    ) {
        this.audioAnalysisService = audioAnalysisService;
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.lightingService = lightingService;
        this.fixturePixelGroupResolveService = fixturePixelGroupResolveService;

        if (settingsService.getSettings().getDesignerLivePreview()) {
            startPreview(0);
        }

        this.buildDesignerCache();
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    private void buildDesignerCache() {
        // Load all designer files
        File folder;
        File[] fileList;

        logger.debug("Get all designer projects");

        folder = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath());
        fileList = folder.listFiles();

        // read while the compositions play (how long they last), changed when a project is saved
        projects = new CopyOnWriteArrayList<>();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    logger.debug("Found project '" + file.getName() + "'");

                    // A project, which can't be read, must not take the others with it
                    try {
                        Project project = DesignerJson.MAPPER.readValue(file, Project.class);

                        projects.add(project);
                    } catch (Exception e) {
                        logger.error("Could not load the designer project '{}'", file.getName(), e);
                    }
                }
            }
        }
    }

    @Override
    public Project getProjectByCompositionName(String compositionName) {
        // Return the project for a specified composition (only one project is supported)
        for (Project project : projects) {
            for (Composition composition : project.getCompositions()) {
                if (compositionName.equals(composition.getName())) {
                    return project;
                }
            }
        }

        return null;
    }

    // Where the lights of the designer composition of that name have gone out (0 without one). The composition plays
    // at least that long, like the designer plays a composition without an audio file.
    @Override
    public long getCompositionEndMillis(String compositionName) {
        Project project = compositionName == null ? null : getProjectByCompositionName(compositionName);

        if (project == null) {
            return 0;
        }

        // the last millisecond with lights, the parts of one included
        return (long) Math.ceil(getClipsEndMillis(project, getCompositionByName(project, compositionName)));
    }

    // The end of the last clip of the composition, or of a fade running past the end of a clip (of its scene, or of one
    // of the presets played in it). Worked out like the designer does it.
    static double getClipsEndMillis(Project project, Composition composition) {
        double endMillis = 0;

        if (composition == null || composition.getScenePlaybackRegions() == null || project.getScenes() == null) {
            return endMillis;
        }

        for (ScenePlaybackRegion region : composition.getScenePlaybackRegions()) {
            Scene scene = project.getScenes().stream().filter(candidate -> candidate.getUuid().equals(region.getSceneUuid())).findFirst().orElse(null);

            // a clip without its scene is not played
            if (scene == null) {
                continue;
            }

            // the scene ends with its clip, or later when it fades out past it
            endMillis = Math.max(endMillis, getSceneEndMillis(scene, region));

            if (scene.getPresetUuids() == null || project.getPresets() == null) {
                continue;
            }

            for (String presetUuid : scene.getPresetUuids()) {
                Preset preset = project.getPresets().stream().filter(candidate -> presetUuid.equals(candidate.getUuid())).findFirst().orElse(null);

                if (preset == null) {
                    continue;
                }

                endMillis = Math.max(endMillis, getPresetPlayEndMillis(preset, region, scene));
            }
        }

        return endMillis;
    }

    // When the scene of a clip plays: its fades may reach outside the clip
    static double getSceneStartMillis(Scene scene, ScenePlaybackRegion region) {
        return scene != null && scene.isFadeInPre() ? region.getStartMillis() - scene.getFadeInMillis() : region.getStartMillis();
    }

    static double getSceneEndMillis(Scene scene, ScenePlaybackRegion region) {
        return scene != null && scene.isFadeOutPost() ? region.getEndMillis() + scene.getFadeOutMillis() : region.getEndMillis();
    }

    // When a preset played in the scene of a clip plays, its own fades outside its times included. A preset without
    // times of its own plays as long as the scene does, also while the scene fades in or out outside the clip.
    static double getPresetPlayStartMillis(Preset preset, ScenePlaybackRegion region, Scene scene) {
        double startMillis = preset.getStartMillis() == null ? getSceneStartMillis(scene, region) : region.getStartMillis() + preset.getStartMillis();

        return startMillis - (preset.isFadeInPre() ? preset.getFadeInMillis() : 0);
    }

    static double getPresetPlayEndMillis(Preset preset, ScenePlaybackRegion region, Scene scene) {
        double endMillis = preset.getEndMillis() == null ? getSceneEndMillis(scene, region) : region.getStartMillis() + preset.getEndMillis();

        return endMillis + (preset.isFadeOutPost() ? preset.getFadeOutMillis() : 0);
    }

    @Override
    public Project getProjectByName(String name) {
        // Return the project for a specified name
        for (Project project : projects) {
            if (project.getName().equals(name)) {
                return project;
            }
        }

        return null;
    }

    @Override
    public void deleteProjectByName(String name) {
        // Delete the project
        File file = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath() + File.separator + FileNames.check(name) + ".json");

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete project '" + name + "'");
            }
        }

        // Return the project for a specified name
        for (Project project : projects) {
            if (project.getName().equals(name)) {
                projects.remove(project);
                break;
            }
        }
    }

    @Override
    public List<Project> getAllProjects() {
        return projects;
    }

    @Override
    public void saveProject(String project) throws IOException {
        // Save a project as string, because we will not access properties
        // and unmarshalling might be incomplete, because the backend only cares
        // about a part of the properties not all (e.g. preview-related ones).
        Project projectObject = DesignerJson.MAPPER.readValue(project, Project.class);
        String projectName = FileNames.check(projectObject.getName());
        String designerPath = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath();

        createDirectoryIfNotExists(designerPath);

        AtomicFile.writeString(new File(designerPath + File.separator + projectName + ".json"), project);

        // Add the file to the cache
        for (Project existingProject : projects) {
            if (existingProject.getName().equals(projectName)) {
                projects.remove(existingProject);
                break;
            }
        }
        projects.add(projectObject);

        logger.info("Designer project '" + projectName + "' saved");
    }

    // A composition was renamed: the projects playing along with it keep it under its new name. The files are changed
    // as JSON, so the parts of the projects the app doesn't read stay as they are.
    @Override
    public synchronized void renameComposition(String previousName, String name) throws IOException {
        String designerPath = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath();
        ObjectMapper mapper = new ObjectMapper();

        for (Project project : new ArrayList<>(projects)) {
            File file = new File(designerPath + File.separator + FileNames.check(project.getName()) + ".json");

            if (!file.exists()) {
                continue;
            }

            JsonNode projectNode = mapper.readTree(file);
            boolean renamed = false;

            for (JsonNode compositionNode : projectNode.path("compositions")) {
                if (compositionNode instanceof ObjectNode compositionObject && compositionNode.path("name").isString()
                        && previousName.equals(compositionNode.path("name").stringValue())) {

                    compositionObject.put("name", name);
                    renamed = true;
                }
            }

            if (renamed) {
                saveProject(mapper.writeValueAsString(projectNode));
            }
        }
    }

    private Fixture getFixtureByUuid(Project project, String uuid) {
        for (Fixture fixture : project.getFixtures()) {
            if (Objects.equals(fixture.getUuid(), uuid)) {
                return fixture;
            }
        }
        return null;
    }

    private Composition getCompositionByName(Project project, String compositionName) {
        // Return the project for a specified composition (only one project is supported)
        for (Composition composition : project.getCompositions()) {
            if (compositionName.equals(composition.getName())) {
                return composition;
            }
        }

        return null;
    }

    private static Composition getCompositionByUuid(Project project, String uuid) {
        if (project == null || uuid == null || project.getCompositions() == null) {
            return null;
        }

        for (Composition composition : project.getCompositions()) {
            if (uuid.equals(composition.getUuid())) {
                return composition;
            }
        }

        return null;
    }

    private Preset getPresetByUuid(String presetUuid) {
        for (Preset preset : project.getPresets()) {
            if (presetUuid.equals(preset.getUuid())) {
                return preset;
            }
        }

        return null;
    }

    private Scene getSceneByUuid(String sceneUuid) {
        for (Scene scene : project.getScenes()) {
            if (scene.getUuid().equals(sceneUuid)) {
                return scene;
            }
        }

        return null;
    }

    // the clips of a track, in the order they are played
    static List<ScenePlaybackRegion> getTrackRegions(Composition composition, String trackUuid) {
        if (composition.getScenePlaybackRegions() == null) {
            return new ArrayList<>();
        }

        return Arrays.stream(composition.getScenePlaybackRegions())
                .filter(region -> trackUuid.equals(region.getTrackUuid()))
                .sorted(Comparator.comparingDouble(ScenePlaybackRegion::getStartMillis))
                .collect(Collectors.toList());
    }

    // The tracks the preview of the designer plays: a hidden track is left out, and once a track is soloed only the
    // soloed ones are played. The show itself always plays every track.
    static List<Track> getPreviewTracks(Composition composition) {
        List<Track> shown = composition.getTracks().stream().filter(track -> !track.isHidden()).collect(Collectors.toList());
        List<Track> soloed = shown.stream().filter(Track::isSolo).collect(Collectors.toList());

        return soloed.isEmpty() ? shown : soloed;
    }

    // all presets of a scene in their layer order: the first one is the topmost layer,
    // overwriting the values of the ones below it
    private List<Preset> getScenePresets(Scene scene) {
        List<Preset> presets = new ArrayList<>();

        if (scene.getPresetUuids() == null) {
            return presets;
        }

        for (String presetUuid : scene.getPresetUuids()) {
            Preset preset = getPresetByUuid(presetUuid);

            // a preset can only be once in a scene
            if (preset != null && !presets.contains(preset)) {
                presets.add(preset);
            }
        }

        return presets;
    }

    private List<PresetRegionScene> getPresetsInTime(double timeMillis) {
        // Return all presets which should be active during the specified time, the ones at the bottom first: the ones
        // processed later overwrite them
        List<PresetRegionScene> activePresets = new ArrayList<>();

        // The tracks are layered like the presets inside a scene: the topmost one is processed last, so it overwrites
        // the ones below it. On a track, a clip fading out past its end lies below the one following it. Hiding and
        // soloing tracks only helps working on the show: the designer previews its project without a composition
        // player (see DesignerController.preview), the show is played by one and plays all of its tracks.
        List<Track> tracks = compositionPlayer == null ? getPreviewTracks(composition) : composition.getTracks();

        for (int trackIndex = tracks.size() - 1; trackIndex >= 0; trackIndex--) {
            for (ScenePlaybackRegion region : getTrackRegions(composition, tracks.get(trackIndex).getUuid())) {
                Scene scene = getSceneByUuid(region.getSceneUuid());

                if (scene == null) {
                    continue;
                }

                // the presets are ordered inside their scene: the first one is the topmost
                // layer -> process it last, so it overwrites the ones below it
                List<Preset> scenePresets = getScenePresets(scene);

                for (int presetIndex = scenePresets.size() - 1; presetIndex >= 0; presetIndex--) {
                    Preset preset = scenePresets.get(presetIndex);

                    // the fades running outside the boundaries (of the preset, or of the scene) included
                    if (getPresetPlayStartMillis(preset, region, scene) <= timeMillis && getPresetPlayEndMillis(preset, region, scene) >= timeMillis) {
                        activePresets.add(new PresetRegionScene(preset, region, scene));
                    }
                }
            }
        }

        return activePresets;
    }

    private List<PresetRegionScene> getPresets(double timeMillis) {
        // Get relevant presets in correct order to process with their corresponding scene, if available
        List<PresetRegionScene> presets = new ArrayList<>();

        if (playPreview || (compositionPlayer != null && (holding || CompositionPlayer.PlayState.PLAYING.equals(compositionPlayer.getPlayState())))) {
            // Only use active presets in current regions (also while the timeline holds at a hold point)
            presets = getPresetsInTime(timeMillis);
            updateRegionStartBeats(presets, timeMillis);
        } else if (playingManualScenes) {
            // the scenes picked by hand, each from the moment it was picked, fading in and out (see LiveScenes)
            presets = liveScenes.getPresets(project.getScenes() == null ? List.of() : project.getScenes(), this::getScenePresets, timeMillis, System.currentTimeMillis());
        } else {
            // previewPreset = solo. Without a selected scene there is nothing else to
            // show than the selected preset anyway.
            if (previewPreset || selectedSceneUuids == null || selectedSceneUuids.isEmpty()) {
                // Only preview the selected preset (if it's still there: the designer may have deleted it)
                Preset selectedPreset = selectedPresetUuid == null ? null : getPresetByUuid(selectedPresetUuid);

                if (selectedPreset != null) {
                    presets.add(new PresetRegionScene(selectedPreset, null, null));
                }
            } else {
                // Preview the selected scenes
                for (int sceneIndex = project.getScenes().size() - 1; sceneIndex >= 0; sceneIndex--) {
                    Scene scene = project.getScenes().get(sceneIndex);

                    if (!selectedSceneUuids.contains(scene.getUuid())) {
                        continue;
                    }

                    // the presets are ordered inside their scene: the first one is the
                    // topmost layer -> process it last, so it overwrites the ones below it
                    List<Preset> scenePresets = getScenePresets(scene);

                    for (int presetIndex = scenePresets.size() - 1; presetIndex >= 0; presetIndex--) {
                        presets.add(new PresetRegionScene(scenePresets.get(presetIndex), null, scene));
                    }
                }
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Presets in time:");
            for (PresetRegionScene preset : presets) {
                logger.trace("- " + preset.getPreset().getUuid());
            }
        }

        return presets;
    }

    private void mixChannelValue(List<FixtureChannelValue> existingChannelValues, FixtureChannelValue channelValue, double intensityPercentage, double defaultValue) {
        double newValue = channelValue.getValue();
        double existingValue = defaultValue;

        if (intensityPercentage < 1) {
            // We need to mix a possibly existing value (or the default value 0) with the new value (fading)

            // Get the existent value for this property
            for (FixtureChannelValue existingChannelValue : existingChannelValues) {
                if (existingChannelValue.getChannelName().equals(channelValue.getChannelName()) && existingChannelValue.getProfileUuid().equals(channelValue.getProfileUuid())) {
                    existingValue = existingChannelValue.getValue();
                    break;
                }
            }

            // Mix the existing value with the new value
            newValue = existingValue * (1 - intensityPercentage) + newValue * intensityPercentage;

            logger.trace("existingValue: " + existingValue + ", intensityPercentage: " + intensityPercentage + ", newValue: " + newValue);
        }

        // Remove the existent value, if available
        Iterator<FixtureChannelValue> iterator = existingChannelValues.iterator();
        while (iterator.hasNext()) {
            FixtureChannelValue fixtureChannelValue = iterator.next();
            if (fixtureChannelValue.getChannelName().equals(channelValue.getChannelName()) && fixtureChannelValue.getProfileUuid().equals(channelValue.getProfileUuid())) {
                iterator.remove();
                break;
            }
        }

        // Add the new value
        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
        fixtureChannelValue.setChannelName(channelValue.getChannelName());
        fixtureChannelValue.setProfileUuid(channelValue.getProfileUuid());
        fixtureChannelValue.setValue(newValue);
        existingChannelValues.add(fixtureChannelValue);
    }

    private void mixChannelValue(List<FixtureChannelValue> existingChannelValues, FixtureChannelValue channelValue, double intensityPercentage) {
        mixChannelValue(existingChannelValues, channelValue, intensityPercentage, 0);
    }

    // like in the designer, an empty name is none (e.g. no pixel key or no wheel)
    static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    private boolean fixtureUuidAndPixelKeyEquals(String fixtureUuid1, String fixtureUuid2, String pixelKey1, String pixelKey2) {
        return fixtureUuid1.equals(fixtureUuid2) && Objects.equals(emptyToNull(pixelKey1), emptyToNull(pixelKey2));
    }

    private DmxChannelAndPixelKey getDmxChannelAndPixelKey(PresetFixture presetFixture) {
        Fixture fixture = getFixtureByUuid(project, presetFixture.getFixtureUuid());

        if (fixture == null) {
            return null;
        }

        DmxChannelAndPixelKey dmxChannelAndPixelKey = new DmxChannelAndPixelKey();
        dmxChannelAndPixelKey.setDmxChannel(fixture.getDmxFirstChannel());
        dmxChannelAndPixelKey.setPixelKey(emptyToNull(presetFixture.getPixelKey()));
        dmxChannelAndPixelKey.setDmxUniverseUuid(fixture.getDmxUniverseUuid());

        return dmxChannelAndPixelKey;
    }

    // The place of each fixture (or pixel) of a preset in its chase and the number of steps of the chase. Fixtures
    // sharing a DMX universe, address and pixel key are the same lamp and therefore take one step only.
    private PresetChase calculatePresetChase(Preset preset) {
        Map<PresetFixtureKey, Integer> fixtureIndices = new HashMap<>();

        Set<PresetFixtureKey> presetFixtures = new HashSet<>();
        for (PresetFixture presetFixture : preset.getFixtures()) {
            presetFixtures.add(new PresetFixtureKey(presetFixture.getFixtureUuid(), presetFixture.getPixelKey()));
        }

        // either the global fixture order of the project or the preset's own one defines
        // the order to chase in
        List<PresetFixture> orderedFixtures = preset.isUseGlobalFixtureOrder() ? project.getPresetFixtures() : preset.getFixtures();
        Set<DmxChannelAndPixelKey> counted = new HashSet<>();
        int index = 0;
        Map<String, Integer> wholeFixtureIndices = new HashMap<>();
        Set<String> wholeCounted = new HashSet<>();
        int wholeIndex = 0;

        for (PresetFixture orderedFixture : orderedFixtures) {
            PresetFixtureKey key = new PresetFixtureKey(orderedFixture.getFixtureUuid(), orderedFixture.getPixelKey());

            // when using the global order, skip all fixtures which are not in this preset
            if (!presetFixtures.contains(key)) {
                continue;
            }

            // a fixture listed more than once chases at its first place
            fixtureIndices.putIfAbsent(key, index);

            // don't count fixtures on the same channel as already counted ones
            DmxChannelAndPixelKey firstDmxChannelAndPixelKey = getDmxChannelAndPixelKey(orderedFixture);
            if (firstDmxChannelAndPixelKey != null && counted.add(firstDmxChannelAndPixelKey)) {
                index++;
            }

            // a fixture with all of its pixels takes one step, the same for the fixtures on its channel
            wholeFixtureIndices.putIfAbsent(orderedFixture.getFixtureUuid(), wholeIndex);
            String wholeLamp = getWholeLampKey(orderedFixture);
            if (wholeLamp != null && wholeCounted.add(wholeLamp)) {
                wholeIndex++;
            }
        }

        Set<DmxChannelAndPixelKey> lamps = new HashSet<>();
        for (PresetFixture presetFixture : preset.getFixtures()) {
            DmxChannelAndPixelKey firstDmxChannelAndPixelKey = getDmxChannelAndPixelKey(presetFixture);
            if (firstDmxChannelAndPixelKey != null) {
                lamps.add(firstDmxChannelAndPixelKey);
            }
        }

        Set<String> wholeLamps = new HashSet<>();
        for (PresetFixture presetFixture : preset.getFixtures()) {
            String wholeLamp = getWholeLampKey(presetFixture);
            if (wholeLamp != null) {
                wholeLamps.add(wholeLamp);
            }
        }

        return new PresetChase(fixtureIndices, lamps.size(), wholeFixtureIndices, wholeLamps.size());
    }

    // the fixture of an entry of a preset with all of its pixels: fixtures sharing a DMX universe and address are the
    // same lamp. Null for a fixture which is gone.
    private String getWholeLampKey(PresetFixture presetFixture) {
        Fixture fixture = getFixtureByUuid(project, presetFixture.getFixtureUuid());

        return fixture == null ? null : fixture.getDmxUniverseUuid() + "\u0000" + fixture.getDmxFirstChannel();
    }

    // The chase of a preset doesn't change while its project plays: it's worked out once, instead of for each fixture
    // on every frame, which took most of the time of a frame with a few dozen fixtures
    private PresetChase getPresetChase(Preset preset) {
        return presetChases.computeIfAbsent(preset, this::calculatePresetChase);
    }

    // Get the fixture index inside the passed preset (used for chasing), null if the fixture is not in the preset
    private Integer getFixtureIndex(Preset preset, String fixtureUuid, String pixelKey) {
        return getPresetChase(preset).fixtureIndices().get(new PresetFixtureKey(fixtureUuid, pixelKey));
    }

    // the number of chase steps of a preset
    private int getPresetFixtureCount(Preset preset) {
        return getPresetChase(preset).fixtureCount();
    }

    private FixtureProfile getProfileByUuid(String uuid) {
        return getProfileByUuid(project, uuid);
    }

    private static FixtureProfile getProfileByUuid(Project project, String uuid) {
        if (project == null || project.getFixtureProfiles() == null) {
            return null;
        }

        for (FixtureProfile fixtureProfile : project.getFixtureProfiles()) {
            if (fixtureProfile.getUuid().equals(uuid)) {
                return fixtureProfile;
            }
        }

        return null;
    }

    private FixtureMode getModeByFixture(FixtureProfile profile, Fixture fixture) {
        for (FixtureMode mode : profile.getModes()) {
            if (mode.getShortName() != null && !mode.getShortName().isEmpty()) {
                if (mode.getShortName().equals(fixture.getModeShortName())) {
                    return mode;
                }
            } else {
                if (mode.getName().equals(fixture.getModeShortName())) {
                    return mode;
                }
            }
        }

        return null;
    }

    private static double getMaxValueByChannel(FixtureChannel fixtureChannel) {
        return Math.pow(256, 1 + fixtureChannel.getFineChannelAliases().size()) - 1;
    }

    // The value a channel starts out with, or null for none: a DMX value or a percentage of the channel (the fixture
    // library also has fractions, e.g. 33.3%). One which can't be read is none, like in the designer.
    static Double getDefaultValueByChannel(FixtureChannel fixtureChannel) {
        String defaultValue = fixtureChannel.getDefaultValue();

        if (defaultValue == null) {
            return null;
        }

        if (defaultValue.endsWith("%")) {
            // percentage value
            Double percentage = parseNumber(defaultValue.substring(0, defaultValue.length() - 1));
            return percentage == null ? null : getMaxValueByChannel(fixtureChannel) / 100 * percentage;
        }

        // DMX value, of a profile writing it in fewer bits than the channel has as well
        Double value = parseNumber(defaultValue);

        return value == null ? null : value * FixtureChannel.getResolutionMultiplier(fixtureChannel);
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    // A number written in a fixture profile (e.g. "12.5", surrounding spaces allowed), or null if the text is none. The
    // designer reads them the very same way, which neither Double.parseDouble ("1d", "NaN") nor Number() ("0x10") do.
    static Double parseNumber(String text) {
        String trimmed = text.trim();
        return NUMBER.matcher(trimmed).matches() ? Double.parseDouble(trimmed) : null;
    }

    private boolean wheelHasSlotType(FixtureWheel wheel, FixtureWheelSlot.FixtureWheelSlotType slotType) {
        if (wheel == null || wheel.getSlots() == null) {
            // a slot of a wheel the profile doesn't have (e.g. a channel named differently than its wheel)
            return false;
        }

        for (FixtureWheelSlot slot : wheel.getSlots()) {
            if (slot.getType() == slotType) {
                return true;
            }
        }

        return false;
    }

    private CachedFixtureCapability getFixtureCapability(FixtureCapability capability, String channelName, FixtureProfile profile, double resolutionMultiplier) {
        CachedFixtureCapability cachedFixtureCapability = new CachedFixtureCapability();
        cachedFixtureCapability.setCapability(FixtureChannel.getCapabilityInFullResolution(capability, resolutionMultiplier));
        if (capability.getSlotNumber() != null) {
            // there is a wheel connected to this capability
            if (capability.getWheel() instanceof String) {
                cachedFixtureCapability.setWheelName((String) capability.getWheel());
            } else if (capability.getWheel() instanceof List<?> wheels) {
                // a capability of several wheels (e.g. shaking them all) names them in a list (a JSON array is read
                // as one): the first one, like in the designer
                cachedFixtureCapability.setWheelName(wheels.isEmpty() ? null : String.valueOf(wheels.getFirst()));
            } else {
                cachedFixtureCapability.setWheelName(channelName);
            }
            cachedFixtureCapability.setWheel(getWheelByName(profile, cachedFixtureCapability.getWheelName()));
            cachedFixtureCapability.setWheelSlots(getWheelSlots(cachedFixtureCapability.getWheel(), capability.getSlotNumber()));
            cachedFixtureCapability.setWheelIsColor(wheelHasSlotType(cachedFixtureCapability.getWheel(), FixtureWheelSlot.FixtureWheelSlotType.Color));
        }
        if (cachedFixtureCapability.getCapability().getDmxRange() != null && cachedFixtureCapability.getCapability().getDmxRange().size() == 2) {
            cachedFixtureCapability.setCenterValue((int) Math.floor((cachedFixtureCapability.getCapability().getDmxRange().get(0) + cachedFixtureCapability.getCapability().getDmxRange().get(1)) / 2f));
        }
        return cachedFixtureCapability;
    }

    private List<CachedFixtureCapability> getCapabilitiesByChannel(FixtureChannel fixtureChannel, String channelName, FixtureProfile profile) {
        List<CachedFixtureCapability> capabilities = new ArrayList<>();

        // the ranges of a profile writing them in fewer bits than the channel has, in its full resolution
        double multiplier = FixtureChannel.getResolutionMultiplier(fixtureChannel);

        if (fixtureChannel.getCapability() != null) {
            capabilities.add(getFixtureCapability(fixtureChannel.getCapability(), channelName, profile, multiplier));
        } else if (fixtureChannel.getCapabilities() != null) {
            for (FixtureCapability capability : fixtureChannel.getCapabilities()) {
                capabilities.add(getFixtureCapability(capability, channelName, profile, multiplier));
            }
        }

        return capabilities;
    }

    private boolean capabilitiesMatch(FixtureCapability.FixtureCapabilityType type1, FixtureCapability.FixtureCapabilityType type2, FixtureCapability.FixtureCapabilityColor color1, FixtureCapability.FixtureCapabilityColor color2, String wheel1, String wheel2, String profileUuid1, String profileUuid2) {
        // checks, whether two provided capapabilities match
        // an empty wheel or profile names none, like in the designer
        return type1 == type2 && (color1 == null || color1 == color2) && (emptyToNull(wheel1) == null || wheel1.equals(wheel2)) && (emptyToNull(profileUuid1) == null || profileUuid1.equals(profileUuid2));
    }

    private FixtureCapabilityValue getCapabilityValue(List<FixtureCapabilityValue> capabilityValues, FixtureCapability.FixtureCapabilityType capabilityType, FixtureCapability.FixtureCapabilityColor color, String wheel, String profileUuid) {
        for (FixtureCapabilityValue capabilityValue : capabilityValues) {
            if (capabilitiesMatch(capabilityValue.getType(), capabilityType, capabilityValue.getColor(), color, capabilityValue.getWheel(), wheel, capabilityValue.getProfileUuid(), profileUuid)) {
                return capabilityValue;
            }
        }
        return null;
    }

    private FixtureWheel getWheelByName(FixtureProfile profile, String wheelName) {
        return profile.getWheels().getWheels().get(wheelName);
    }

    // The slot of a wheel by its number, counted from 1 and around the wheel like in the fixture library (e.g. 9 is the
    // first slot of a wheel with 8 slots, 0 the last)
    private static FixtureWheelSlot getWheelSlot(FixtureWheel wheel, int slotNumber) {
        int slotCount = wheel.getSlots().size();
        return wheel.getSlots().get(Math.floorMod(slotNumber - 1, slotCount));
    }

    private List<FixtureWheelSlot> getWheelSlots(FixtureWheel wheel, double slotNumber) {
        // return one slot or two slots, if they are split (e.g. slot number 2.5 returns the slots 2 and 3)
        List<FixtureWheelSlot> slots = new ArrayList<>();

        if (wheel == null || wheel.getSlots() == null || wheel.getSlots().isEmpty()) {
            return slots;
        }

        slots.add(getWheelSlot(wheel, (int) Math.floor(slotNumber)));

        if (slotNumber != Math.floor(slotNumber)) {
            slots.add(getWheelSlot(wheel, (int) Math.ceil(slotNumber)));
        }

        return slots;
    }

    private List<String> getWheelSlotColors(FixtureWheel wheel, double slotNumber) {
        List<String> colors = new ArrayList<>();
        List<FixtureWheelSlot> wheelSlots = getWheelSlots(wheel, slotNumber);

        for (FixtureWheelSlot slot : wheelSlots) {
            if (slot.getColors() != null && !slot.getColors().isEmpty()) {
                colors.addAll(slot.getColors());
            }
        }

        return colors;
    }

    private static final Pattern HEX_COLOR = Pattern.compile("#?([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})");
    private static final Pattern SHORT_HEX_COLOR = Pattern.compile("#?([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])");

    // A color like "#ff8000", also without the "#" or in short ("#f80"), like the designer reads it. Null for anything
    // else.
    static Color hexToRgb(String hex) {
        if (hex == null) {
            return null;
        }

        Matcher shortMatcher = SHORT_HEX_COLOR.matcher(hex);

        if (shortMatcher.matches()) {
            hex = shortMatcher.group(1).repeat(2) + shortMatcher.group(2).repeat(2) + shortMatcher.group(3).repeat(2);
        }

        Matcher matcher = HEX_COLOR.matcher(hex);

        if (!matcher.matches()) {
            return null;
        }

        return new Color((double) Integer.parseInt(matcher.group(1), 16), (double) Integer.parseInt(matcher.group(2), 16), (double) Integer.parseInt(matcher.group(3), 16));
    }

    private static Color mixColors(List<Color> colors) {
        // mix an array of rgb-colors containing r, g, b values to a new color
        Double r = 0d;
        Double g = 0d;
        Double b = 0d;

        for (Color color : colors) {
            r += color.getRed();
            g += color.getGreen();
            b += color.getBlue();
        }

        r /= colors.size();
        g /= colors.size();
        b /= colors.size();

        return new Color(r, g, b);
    }

    private Color getMixedWheelSlotColor(FixtureWheel wheel, double slotNumber) {
        return mixHexColors(getWheelSlotColors(wheel, slotNumber));
    }

    // the passed colors mixed, or null for none
    static Color mixHexColors(List<String> colors) {
        List<Color> colorsRgb = new ArrayList<>();

        for (String color : colors) {
            Color rgb = hexToRgb(color);

            // like the designer, which leaves out a color it can't read
            if (rgb != null) {
                colorsRgb.add(rgb);
            }
        }

        if (colorsRgb.isEmpty()) {
            return null;
        }

        return mixColors(colorsRgb);
    }

    // the color the capability values light (0 to 255 for each of red, green and blue), which a color wheel or the color
    // presets of a fixture show approximated, or null, if they don't name all three
    private double[] getApproximatedColor(List<FixtureCapabilityValue> capabilityValues) {
        FixtureCapabilityValue red = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Red, null, null);
        FixtureCapabilityValue green = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Green, null, null);
        FixtureCapabilityValue blue = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Blue, null, null);

        if (red == null || green == null || blue == null) {
            return null;
        }

        // a color without a value is none, like in the designer
        return new double[]{
                red.getValuePercentage() == null ? Double.NaN : 255 * red.getValuePercentage(),
                green.getValuePercentage() == null ? Double.NaN : 255 * green.getValuePercentage(),
                blue.getValuePercentage() == null ? Double.NaN : 255 * blue.getValuePercentage()
        };
    }

    // the capability of the channel closest to the color the capability values light, of the passed colors
    private CachedFixtureCapability getClosestColorCapability(List<FixtureCapabilityValue> capabilityValues, CachedFixtureChannel cachedChannel, Function<CachedFixtureCapability, Color> colorOf) {
        double[] color = getApproximatedColor(capabilityValues);
        double lowestDiff = Double.MAX_VALUE;
        CachedFixtureCapability lowestDiffCapability = null;

        if (color == null) {
            return null;
        }

        for (CachedFixtureCapability capability : cachedChannel.getCapabilities()) {
            Color mixedColor = colorOf.apply(capability);

            if (mixedColor != null) {
                double diff = Math.abs(mixedColor.getRed() - color[0]) + Math.abs(mixedColor.getGreen() - color[1]) + Math.abs(mixedColor.getBlue() - color[2]);

                if (diff < lowestDiff) {
                    lowestDiff = diff;
                    lowestDiffCapability = capability;
                }
            }
        }

        return lowestDiffCapability;
    }

    private CachedFixtureCapability getApproximatedColorWheelCapability(List<FixtureCapabilityValue> capabilityValues, CachedFixtureChannel cachedChannel) {
        // return an approximated wheel slot channel capability, if a color or a slot on a different
        // wheel has been selected
        return getClosestColorCapability(capabilityValues, cachedChannel, capability -> capability.getCapability().getSlotNumber() == null
                ? null
                : getMixedWheelSlotColor(capability.getWheel(), capability.getCapability().getSlotNumber()));
    }

    // The color preset (e.g. a color macro) of the channel closest to the color the capability values light, which a
    // fixture without colors and without a color wheel of its own shows (see mixCapabilityValues), or null, if the
    // channel has no preset showing a color, like in the designer
    private CachedFixtureCapability getApproximatedColorPresetCapability(List<FixtureCapabilityValue> capabilityValues, CachedFixtureChannel cachedChannel) {
        return getClosestColorCapability(capabilityValues, cachedChannel, capability -> {
            FixtureCapability fixtureCapability = capability.getCapability();
            boolean hasRange = fixtureCapability.getDmxRange() != null && fixtureCapability.getDmxRange().size() == 2;

            return fixtureCapability.getType() == FixtureCapability.FixtureCapabilityType.ColorPreset && hasRange && fixtureCapability.getColors() != null
                    ? mixHexColors(fixtureCapability.getColors())
                    : null;
        });
    }

    // the lamp a part of a fixture is: its universe, its DMX address and its pixel
    private record LampKey(String universeUuid, int dmxFirstChannel, String pixelKey) {
    }

    // For each of the passed fixtures, the first one before it with the same universe, DMX address and pixel (the same
    // lamp), or -1
    private static int[] getSameLampIndices(List<CachedFixture> fixtures) {
        int[] indices = new int[fixtures.size()];
        Map<LampKey, Integer> firstOfLamp = new HashMap<>();

        for (int i = 0; i < fixtures.size(); i++) {
            CachedFixture fixture = fixtures.get(i);
            LampKey key = new LampKey(fixture.getFixture().getDmxUniverseUuid(), fixture.getFixture().getDmxFirstChannel(), fixture.getPixel() == null ? null : emptyToNull(fixture.getPixel().getKey()));
            Integer first = firstOfLamp.putIfAbsent(key, i);
            indices[i] = first == null ? -1 : first;
        }

        return indices;
    }

    // where the preset itself starts, which is what its steps are timed against. The
    // fades may reach outside it, but a step at 0 belongs to the preset's own start.
    private double getPresetStartMillis(PresetRegionScene preset) {
        if (preset.getRegion() == null) {
            return 0;
        }

        return preset.getPreset().getStartMillis() == null ? preset.getRegion().getStartMillis() : preset.getRegion().getStartMillis() + preset.getPreset().getStartMillis();
    }

    // the step the designer is editing, which is what the preview holds while nothing is
    // playing. The presets which are not being edited show what they start out with.
    private PresetStep getEditStep(Preset preset) {
        List<PresetStep> steps = preset.getSteps();

        if (steps == null || steps.isEmpty()) {
            return null;
        }

        if (selectedStepUuid != null && preset.getUuid() != null && preset.getUuid().equals(selectedPresetUuid)) {
            for (PresetStep step : steps) {
                if (selectedStepUuid.equals(step.getUuid())) {
                    return step;
                }
            }
        }

        return steps.get(0);
    }

    // the values a preset applies at the passed time. While nothing is playing, the
    // designer's panels are editing one step, so that is the one to show: the sequence
    // only runs on the timeline, or when the designer asks to watch it.
    // A preset chasing its steps over its fixtures is the passed phasing behind for a fixture. Steps following the beat
    // run as much faster or slower as the tempo played is than the one they are written at; the clips spanning a hold
    // ran on while the timeline held. The designer works it out the same way, in the same order.
    private PresetStepState getPresetStepState(PresetRegionScene preset, double timeMillis, double phasingMillis) {
        boolean followsBeat = "beats".equals(preset.getPreset().getStepsLengthMode());

        if (preset.getRegion() != null) {
            double startMillis = getPresetStartMillis(preset);
            double heldMillis = preset instanceof LiveScenes.LivePreset ? 0 : CompositionHold.getHeldMillis(holds, startMillis, timeMillis);

            if (followsBeat) {
                double beatsPerMinute = isOnTheBeatClock(preset) ? beatClock.getBeatsPerMinute() : getStepsBeatsPerMinute();
                Double beatTimeMillis = getBeatTimeMillis(preset, beatsPerMinute);

                // on the beats of the tempo from outside, from where the preset starts in its clip
                double presetTimeMillis = beatTimeMillis == null ? timeMillis - startMillis + heldMillis : beatTimeMillis - (startMillis - preset.getRegion().getStartMillis());
                return PresetSteps.getStateAtMillis(preset.getPreset(), PresetSteps.getStepsTimeMillis(preset.getPreset(), presetTimeMillis, beatsPerMinute) - phasingMillis);
            }

            return PresetSteps.getStateAtMillis(preset.getPreset(), timeMillis - phasingMillis - startMillis + heldMillis);
        }

        if (stepPreviewRunning) {
            // the sequence was started by hand, so it runs from the point it was started at
            if (followsBeat) {
                double presetTimeMillis = timeMillis - stepPreviewStartMillis;
                return PresetSteps.getStateAtMillis(preset.getPreset(), PresetSteps.getStepsTimeMillis(preset.getPreset(), presetTimeMillis, getStepsBeatsPerMinute()) - phasingMillis);
            }

            return PresetSteps.getStateAtMillis(preset.getPreset(), timeMillis - phasingMillis - stepPreviewStartMillis);
        }

        List<PresetStep> steps = preset.getPreset().getSteps();

        if (steps == null || steps.isEmpty()) {
            // an old project keeps its values on the preset itself
            return PresetSteps.getStateAtMillis(preset.getPreset(), 0);
        }

        return PresetSteps.getStepState(getEditStep(preset.getPreset()));
    }

    // worked out from the passed preset alone, so the specs can run it without a service
    static double getPresetIntensity(PresetRegionScene preset, double timeMillis) {
        // When fading is in progress (on preset or scene-level), the current preset does not
        // fully cover underlying values.
        // -> 0 = no covering at all, 1 = fully cover (no fading)
        double intensityPercentageScene = 1;
        double intensityPercentagePreset = 1;

        if (preset.getScene() != null) {
            // the dimmer holds the scene below full for as long as it plays, whether it sits
            // on the timeline or is only being watched in the preview
            intensityPercentageScene = preset.getScene().getDimmer();
        }

        if (preset.getRegion() != null && preset.getScene() != null) {
            // Fade out is stronger than fade in (if they overlap)

            // Take away intensity for scene fading
            double sceneStartMillis = getSceneStartMillis(preset.getScene(), preset.getRegion());
            double sceneEndMillis = getSceneEndMillis(preset.getScene(), preset.getRegion());

            // the curve maps how far the fade has come to how much of the intensity it has
            // already handed over, which shapes a fade out just as well as a fade in
            if (timeMillis > sceneEndMillis - preset.getScene().getFadeOutMillis() && timeMillis < sceneEndMillis) {
                // Scene fades out
                intensityPercentageScene *= TransitionCurve.apply(preset.getScene().getFadeOutCurve(), (sceneEndMillis - timeMillis) / preset.getScene().getFadeOutMillis());
            } else if (timeMillis < sceneStartMillis + preset.getScene().getFadeInMillis() && timeMillis > sceneStartMillis) {
                // Scene fades in
                intensityPercentageScene *= TransitionCurve.apply(preset.getScene().getFadeInCurve(), (timeMillis - sceneStartMillis) / preset.getScene().getFadeInMillis());
            }
        }

        if (preset.getRegion() != null && preset.getPreset() != null) {
            // Take away intensity for preset fading. Without times of its own, the preset plays as long as the scene,
            // its fades outside the clip included.
            double presetStartMillis = getPresetPlayStartMillis(preset.getPreset(), preset.getRegion(), preset.getScene());
            double presetEndMillis = getPresetPlayEndMillis(preset.getPreset(), preset.getRegion(), preset.getScene());

            if (timeMillis > presetEndMillis - preset.getPreset().getFadeOutMillis() && timeMillis < presetEndMillis) {
                // Preset fades out
                intensityPercentagePreset = TransitionCurve.apply(preset.getPreset().getFadeOutCurve(), (presetEndMillis - timeMillis) / preset.getPreset().getFadeOutMillis());
            } else if (timeMillis < presetStartMillis + preset.getPreset().getFadeInMillis() && timeMillis > presetStartMillis) {
                // Preset fades in
                intensityPercentagePreset = TransitionCurve.apply(preset.getPreset().getFadeInCurve(), (timeMillis - presetStartMillis) / preset.getPreset().getFadeInMillis());
            }
        }

        return intensityPercentageScene * intensityPercentagePreset;
    }

    // whether a capability value gives a value (the designer checks valuePercentage >= 0, which also leaves out a
    // missing one)
    private static boolean hasValue(FixtureCapabilityValue capabilityValue) {
        return capabilityValue.getValuePercentage() != null && capabilityValue.getValuePercentage() >= 0;
    }

    private static boolean isBrightness(FixtureCapabilityValue capabilityValue) {
        return (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity || capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity)
                && hasValue(capabilityValue);
    }

    private static Double parseBrightnessNumber(String brightness, String unit) {
        return parseNumber(brightness.substring(0, brightness.length() - unit.length()));
    }

    // How bright a capability lights, from 0 to 1, like the fixture library gives it: "off", "dark", "bright", in
    // percent or in lumens (compared to the brightest capability of the channel)
    private static Double getBrightness(String brightness, double maxLumens) {
        if (brightness == null) {
            return null;
        }

        return switch (brightness) {
            case "off" -> 0d;
            case "dark" -> 0.01;
            case "bright" -> 1d;
            default -> {
                if (brightness.endsWith("%")) {
                    Double percent = parseBrightnessNumber(brightness, "%");
                    yield percent == null ? null : percent / 100;
                }
                if (brightness.endsWith("lm") && maxLumens > 0) {
                    Double lumens = parseBrightnessNumber(brightness, "lm");
                    yield lumens == null ? null : lumens / maxLumens;
                }
                yield null;
            }
        };
    }

    private static double getMaxLumens(List<CachedFixtureCapability> capabilities) {
        double maxLumens = 0;

        for (CachedFixtureCapability channelCapability : capabilities) {
            FixtureCapability capability = channelCapability.getCapability();

            for (String brightness : new String[]{capability.getBrightness(), capability.getBrightnessStart(), capability.getBrightnessEnd()}) {
                if (brightness != null && brightness.endsWith("lm")) {
                    Double lumens = parseBrightnessNumber(brightness, "lm");
                    if (lumens != null) {
                        maxLumens = Math.max(maxLumens, lumens);
                    }
                }
            }
        }

        return maxLumens;
    }

    // The brightness at the start and at the end of a capability: from off to bright, if not given
    private static double[] getBrightnessRange(FixtureCapability capability, double maxLumens) {
        Double start;
        Double end;

        if (capability.getBrightness() != null) {
            start = getBrightness(capability.getBrightness(), maxLumens);
            end = start;
        } else {
            start = getBrightness(capability.getBrightnessStart(), maxLumens);
            end = getBrightness(capability.getBrightnessEnd(), maxLumens);
        }

        return new double[]{start == null ? 0 : start, end == null ? 1 : end};
    }

    // Package visible for the tests
    // The value of a channel lighting with the passed brightness (0 to 1). A channel with more capabilities (e.g. a
    // dimmer sharing it with a strobe) uses the capability giving the brightness, or else the closest one (the
    // brighter one, if two are as close).
    Double getBrightnessValue(CachedFixtureChannel channel, List<CachedFixtureCapability> brightnessCapabilities, double brightness) {
        if (brightnessCapabilities.isEmpty()) {
            return null;
        }

        double maxLumens = getMaxLumens(channel.getCapabilities());

        if (channel.getCapabilities().size() == 1) {
            // the whole channel, maybe from bright to off
            double[] range = getBrightnessRange(brightnessCapabilities.getFirst().getCapability(), maxLumens);
            return range[0] > range[1] ? channel.getMaxValue() * (1 - brightness) : channel.getMaxValue() * brightness;
        }

        CachedFixtureCapability best = null;
        double bestDistance = Double.MAX_VALUE;
        double bestBrightness = 0;
        boolean bestIsRange = false;

        for (CachedFixtureCapability channelCapability : brightnessCapabilities) {
            double[] range = getBrightnessRange(channelCapability.getCapability(), maxLumens);
            double low = Math.min(range[0], range[1]);
            double high = Math.max(range[0], range[1]);
            double reached = Math.max(low, Math.min(high, brightness));
            double distance = Math.abs(reached - brightness);
            boolean isRange = low < high;

            boolean better;
            if (best == null || distance < bestDistance - 1e-9) {
                better = true;
            } else if (distance > bestDistance + 1e-9) {
                better = false;
            } else if (distance == 0) {
                // both give it: a range rather than a single brightness, else the first one
                better = isRange && !bestIsRange;
            } else {
                // as close: the brighter one
                better = reached > bestBrightness;
            }

            if (better) {
                best = channelCapability;
                bestDistance = distance;
                bestBrightness = reached;
                bestIsRange = isRange;
            }
        }

        FixtureCapability capability = best.getCapability();
        List<Integer> dmxRange = capability.getDmxRange();

        if (dmxRange == null || dmxRange.size() < 2) {
            return null;
        }

        double[] range = getBrightnessRange(capability, maxLumens);

        if (range[0] == range[1]) {
            return (double) best.getCenterValue();
        }

        return dmxRange.get(0) + (dmxRange.get(1) - dmxRange.get(0)) * (bestBrightness - range[0]) / (range[1] - range[0]);
    }

    // The value a curve on a capability puts on a channel of a fixture, played like a value of a step: a dimmer or a
    // color within the range of the channel giving the brightness (not over a strobe sharing the channel), a position
    // (e.g. a zoom, see CapabilityPositions) by the capability of the channel doing it, the pan and the tilt mirrored
    // with the preset. Null, if the channel doesn't do it. The designer plays them the very same way
    // (getCurveChannelValue of its preview).
    private Double getCurveChannelValue(FixtureCapabilityValue capabilityValue, CachedFixtureChannel cachedChannel, Preset preset) {
        FixtureCapability.FixtureCapabilityType type = capabilityValue.getType();

        if (type == FixtureCapability.FixtureCapabilityType.ShutterStrobe) {
            // the strobe on a channel of its speed as well, like the strobe of a step
            Double strobe = CapabilityPositions.getPositionedChannelValue(cachedChannel, type, capabilityValue.getValuePercentage(), null);

            return strobe != null ? strobe : CapabilityPositions.getPositionedChannelValue(cachedChannel, FixtureCapability.FixtureCapabilityType.StrobeSpeed, capabilityValue.getValuePercentage(), null);
        }

        if (CapabilityPositions.isPositionedType(type)) {
            return CapabilityPositions.getPositionedChannelValue(cachedChannel, type, capabilityValue.getValuePercentage(), null);
        }

        List<CachedFixtureCapability> matching = cachedChannel.getCapabilities().stream()
                .filter(channelCapability -> capabilitiesMatch(type, channelCapability.getCapability().getType(), capabilityValue.getColor(), channelCapability.getCapability().getColor(), null, null, null, null))
                .toList();

        if (matching.isEmpty()) {
            return null;
        }

        if (isBrightness(capabilityValue)) {
            // the capability of the channel giving the brightness, of the ones doing it
            return getBrightnessValue(cachedChannel, matching, capabilityValue.getValuePercentage());
        }

        if (PanTilt.isPanTilt(type)) {
            return PanTilt.getPanTiltChannelValue(capabilityValue, cachedChannel, preset != null && preset.mirrorsCapability(type), null);
        }

        return null;
    }

    private static FixtureChannelValue getChannelValue(List<FixtureChannelValue> values, String channelName, String profileUuid) {
        for (FixtureChannelValue value : values) {
            if (value.getChannelName().equals(channelName) && value.getProfileUuid().equals(profileUuid)) {
                return value;
            }
        }

        return null;
    }

    // the capability of a channel the passed value is in
    private static CachedFixtureCapability getCapabilityInValue(CachedFixtureChannel channel, double value) {
        for (CachedFixtureCapability capability : channel.getCapabilities()) {
            List<Integer> dmxRange = capability.getCapability().getDmxRange();

            if (dmxRange == null || dmxRange.size() < 2 || (value >= dmxRange.get(0) && value <= dmxRange.get(1))) {
                return capability;
            }
        }

        return null;
    }

    // how bright a dimmer channel lights at the passed value (0 to 1), like the preview of the designer shows it.
    // Another function sharing the channel (e.g. a strobe) counts as lighting.
    private static double getBrightnessOfValue(CachedFixtureChannel channel, double value) {
        CachedFixtureCapability channelCapability = getCapabilityInValue(channel, value);

        if (channelCapability == null) {
            return 0;
        }

        FixtureCapability capability = channelCapability.getCapability();

        if (capability.getType() != FixtureCapability.FixtureCapabilityType.Intensity) {
            return 1;
        }

        double[] range = getBrightnessRange(capability, getMaxLumens(channel.getCapabilities()));
        List<Integer> dmxRange = capability.getDmxRange();
        double position;

        if (dmxRange != null && dmxRange.size() >= 2) {
            position = dmxRange.get(1).equals(dmxRange.get(0)) ? 0 : (value - dmxRange.get(0)) / (dmxRange.get(1) - dmxRange.get(0));
        } else {
            position = value / channel.getMaxValue();
        }

        return range[0] + (range[1] - range[0]) * position;
    }

    // how bright the dimmer of a fixture (its own or its virtual one) lights with the passed values, or null for a
    // fixture without one (e.g. a pixel of a bar with a master dimmer)
    private static Double getDimmerBrightness(CachedFixture cachedFixture, List<FixtureChannelValue> values) {
        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability capability : channel.getCapabilities()) {
                if (capability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.Intensity) {
                    FixtureChannelValue value = getChannelValue(values, channel.getName(), cachedFixture.getProfile().getUuid());
                    return value == null ? 0 : getBrightnessOfValue(channel, value.getValue());
                }
            }
        }

        return null;
    }

    // how bright a preset lets the dimmer of a fixture light: the dimmer it sets (a curve on the dimmer counts as
    // lighting it), or the one below, if the preset leaves the dimmer alone
    private static double getPresetDimmerBrightness(PresetStepState state, CachedFixture cachedFixture, Preset preset, double brightnessBelow) {
        Double brightness = null;

        for (FixtureCapabilityValue capabilityValue : state.getFixtureCapabilityValues()) {
            if (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity && hasValue(capabilityValue)
                    && (emptyToNull(capabilityValue.getProfileUuid()) == null || capabilityValue.getProfileUuid().equals(cachedFixture.getProfile().getUuid()))) {
                // a later value overwrites an earlier one, like when they are mixed
                brightness = capabilityValue.getValuePercentage();
            }
        }

        if (brightness != null) {
            return brightness;
        }

        for (Effect effect : preset.getEffects()) {
            if (effect instanceof EffectCurve curve && effect.isVisible() && state.getEffectAmount(effect.getUuid()) > 0
                    && curve.getCapabilities().stream().anyMatch(capability -> capability.getType() == FixtureCapability.FixtureCapabilityType.Intensity)) {
                return 1;
            }
        }

        return brightnessBelow;
    }

    // A color of a fading preset on a fixture with a dimmer (its own or a virtual one) doesn't fade on its own: the
    // dimmer carries the fade, or the fixture would get darker twice. The color blends with the one below instead,
    // each of them weighted by how bright it lights, so the light is always the blend of the light below and the light
    // of the preset. A color coming up on a dark fixture is there at once while the dimmer fades it in, and one going
    // dark keeps its color. A fixture without a dimmer fades its colors, they are all there is to fade.
    private void mixColorValue(List<FixtureChannelValue> values, FixtureChannelValue channelValue, double intensityPercentage, Double brightnessBelow, double presetBrightness) {
        double weightBelow = brightnessBelow == null ? 0 : (1 - intensityPercentage) * brightnessBelow;
        double weight = intensityPercentage * presetBrightness;

        if (brightnessBelow == null || weightBelow + weight <= 0) {
            mixChannelValue(values, channelValue, intensityPercentage, 0);
            return;
        }

        if (weightBelow == 0) {
            // nothing to blend with (e.g. the preset doesn't fade)
            mixChannelValue(values, channelValue, 1);
            return;
        }

        FixtureChannelValue below = getChannelValue(values, channelValue.getChannelName(), channelValue.getProfileUuid());
        double valueBelow = below == null ? 0 : below.getValue();

        FixtureChannelValue blendedValue = new FixtureChannelValue();
        blendedValue.setChannelName(channelValue.getChannelName());
        blendedValue.setProfileUuid(channelValue.getProfileUuid());
        blendedValue.setValue((valueBelow * weightBelow + channelValue.getValue() * weight) / (weightBelow + weight));
        mixChannelValue(values, blendedValue, 1);
    }

    // the value a positioned capability value (see CapabilityPositions) drives a channel of a fixture of the passed
    // profile to, or null, if the channel doesn't do it
    private static Double getPositionedChannelValue(FixtureCapabilityValue capabilityValue, CachedFixtureChannel channel, String profileUuid) {
        if (emptyToNull(capabilityValue.getProfileUuid()) != null && !capabilityValue.getProfileUuid().equals(profileUuid)) {
            return null;
        }

        // a program of the fixture, at the speed of the value
        if (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Effect) {
            return Programs.getProgramChannelValue(channel, capabilityValue.getProgramNumber(), capabilityValue.getSoundControlled(), capabilityValue.getValuePercentage());
        }

        // a reset or the lamp switched on or off, on the channel doing it
        if (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Maintenance) {
            Maintenance.Found found = emptyToNull(capabilityValue.getMaintenanceAction()) == null
                    ? null
                    : Maintenance.getCapability(List.of(channel), Maintenance.Action.of(capabilityValue.getMaintenanceAction()));

            return found == null ? null : CapabilityPositions.getCenter(found.capability());
        }

        // a rotation turning (a wheel, if it names one) to an angle and holding it there
        if (CapabilityPositions.isRotationType(capabilityValue.getType()) && capabilityValue.getValueDegrees() != null && !capabilityValue.getValueDegrees().isNaN()) {
            return CapabilityPositions.getRotationAngleChannelValue(channel, capabilityValue.getType(), capabilityValue.getValueDegrees(), capabilityValue.getWheel());
        }

        if (!hasValue(capabilityValue)) {
            return null;
        }

        if (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.WheelShake) {
            return CapabilityPositions.getShakeChannelValue(channel, capabilityValue.getWheel(), capabilityValue.getSlotNumber(), capabilityValue.getValuePercentage());
        }

        return CapabilityPositions.getPositionedChannelValue(channel, capabilityValue.getType(), capabilityValue.getValuePercentage(), capabilityValue.getBlade(), capabilityValue.getShutterEffect(), capabilityValue.getWheel());
    }

    // the value a pan or tilt value points a fixture of the passed profile at with a channel, or null, if the channel
    // doesn't turn it on that axis (see PanTilt). The context places the fixture within its preset and on the stage.
    private static Double getPanTiltChannelValue(FixtureCapabilityValue capabilityValue, CachedFixtureChannel channel, String profileUuid, Preset preset, PanTilt.Context context) {
        if (emptyToNull(capabilityValue.getProfileUuid()) != null && !capabilityValue.getProfileUuid().equals(profileUuid)) {
            return null;
        }

        return PanTilt.getPanTiltChannelValue(capabilityValue, channel, preset.mirrorsCapability(capabilityValue.getType()), context);
    }

    // put a value on a channel of the fixture, over what is on it
    private void mixFixtureChannelValue(List<FixtureChannelValue> values, CachedFixture cachedFixture, String channelName, double value) {
        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
        fixtureChannelValue.setChannelName(channelName);
        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
        fixtureChannelValue.setValue(value);
        this.mixChannelValue(values, fixtureChannelValue, 1);
    }

    private void mixCapabilityValues(PresetStepState state, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage, Preset preset, PanTilt.Context context) {
        boolean hasColor = false;

        // how bright the fixture lights below this preset and how bright the preset lights it, to blend the colors
        // while the preset fades (see mixColorValue)
        Double brightnessBelow = getDimmerBrightness(cachedFixture, values);
        double presetBrightness = brightnessBelow == null ? 0 : getPresetDimmerBrightness(state, cachedFixture, preset, brightnessBelow);

        // a fixture mixing its colors with filters is given the colors the other way round (see SubtractiveColors)
        // and the colors play on the emitters besides red, green and blue (see EmitterColors)
        List<FixtureCapabilityValue> capabilityValues = SubtractiveColors.getFixtureCapabilityValues(state.getFixtureCapabilityValues(), cachedFixture.getChannels(), preset != null && preset.isEmittersFromColor());

        // mix the capability values of the state the preset is in
        for (FixtureCapabilityValue presetCapabilityValue : capabilityValues) {
            for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                if (cachedChannel.getChannel() != null) {
                    if (CapabilityPositions.isOverridingType(presetCapabilityValue.getType())) {
                        // played over the rest below
                        continue;
                    }

                    if (PanTilt.isPanTilt(presetCapabilityValue.getType())) {
                        // a position of a moving head is played on the channel as a whole, by where the fixture is
                        // placed in its preset and on the stage (see PanTilt)
                        Double value = getPanTiltChannelValue(presetCapabilityValue, cachedChannel, cachedFixture.getProfile().getUuid(), preset, context);

                        if (value != null) {
                            mixFixtureChannelValue(values, cachedFixture, cachedChannel.getName(), value);
                        }

                        continue;
                    }

                    if (CapabilityPositions.isPositionedType(presetCapabilityValue.getType()) || CapabilityPositions.isCommandType(presetCapabilityValue.getType())) {
                        // a position (and a program or maintenance) is played on the channel as a whole, by the
                        // capability of it doing it
                        Double value = getPositionedChannelValue(presetCapabilityValue, cachedChannel, cachedFixture.getProfile().getUuid());

                        if (value != null) {
                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                            fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                            fixtureChannelValue.setValue(value);
                            this.mixChannelValue(values, fixtureChannelValue, 1);
                        }

                        continue;
                    }

                    if (isBrightness(presetCapabilityValue)) {
                        // intensity and colorIntensity (dimmer and color): the capabilities of the channel giving it
                        List<CachedFixtureCapability> brightnessCapabilities = cachedChannel.getCapabilities().stream()
                                .filter(channelCapability -> capabilitiesMatch(presetCapabilityValue.getType(), channelCapability.getCapability().getType(), presetCapabilityValue.getColor(), channelCapability.getCapability().getColor(), presetCapabilityValue.getWheel(), channelCapability.getWheelName(), presetCapabilityValue.getProfileUuid(), cachedFixture.getProfile().getUuid()))
                                .toList();
                        Double value = getBrightnessValue(cachedChannel, brightnessCapabilities, presetCapabilityValue.getValuePercentage());

                        if (value != null) {
                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                            fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                            fixtureChannelValue.setValue(value);

                            if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                mixColorValue(values, fixtureChannelValue, intensityPercentage, brightnessBelow, presetBrightness);
                                hasColor = true;
                            } else {
                                this.mixChannelValue(values, fixtureChannelValue, intensityPercentage, 0);
                            }
                        }
                    }

                    for (CachedFixtureCapability channelCapability : cachedChannel.getCapabilities()) {
                        if (capabilitiesMatch(presetCapabilityValue.getType(), channelCapability.getCapability().getType(), presetCapabilityValue.getColor(), channelCapability.getCapability().getColor(), presetCapabilityValue.getWheel(), channelCapability.getWheelName(), presetCapabilityValue.getProfileUuid(), cachedFixture.getProfile().getUuid())) {
                            // the capabilities match -> apply the value, if possible
                            if (isBrightness(presetCapabilityValue)) {
                                // handled for the whole channel above
                                continue;
                            } else if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.WheelSlot && Objects.equals(channelCapability.getCapability().getSlotNumber(), presetCapabilityValue.getSlotNumber())) {
                                // wheel slot (color, gobo, etc.)
                                FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                fixtureChannelValue.setChannelName(cachedChannel.getName());
                                fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                fixtureChannelValue.setValue(channelCapability.getCenterValue());
                                this.mixChannelValue(values, fixtureChannelValue, 1);

                                // check, whether we just set a color wheel value
                                if (channelCapability.isWheelIsColor()) {
                                    hasColor = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // The pulse of an iris or a frost and the shake of a gobo play over what the rest puts on the same channel,
        // whatever order they come in
        for (FixtureCapabilityValue presetCapabilityValue : capabilityValues) {
            if (CapabilityPositions.isOverridingType(presetCapabilityValue.getType())) {
                for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                    if (cachedChannel.getChannel() != null) {
                        Double value = getPositionedChannelValue(presetCapabilityValue, cachedChannel, cachedFixture.getProfile().getUuid());

                        if (value != null) {
                            mixFixtureChannelValue(values, cachedFixture, cachedChannel.getName(), value);
                        }
                    }
                }
            }
        }

        // A fixture which lights no color of its own (with its color channels or a slot of its color wheel) shows the
        // color on its color wheel, approximated from the colors set or from a slot of a different wheel. Only once all
        // the values are mixed, so that the order they come in doesn't decide whether the wheel turns as well.
        if (hasColor) {
            return;
        }

        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
            if (cachedChannel.getColorWheel() != null) {
                CachedFixtureCapability capability = getApproximatedColorWheelCapability(state.getFixtureCapabilityValues(), cachedChannel);

                if (capability != null) {
                    // we found an approximated color in the available wheel channel
                    FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                    fixtureChannelValue.setChannelName(cachedChannel.getName());
                    fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                    fixtureChannelValue.setValue(capability.getCenterValue());
                    this.mixChannelValue(values, fixtureChannelValue, 1);
                }
            }
        }

        // A fixture without a color wheel either shows the color on its color presets (e.g. the color macros of a par
        // lighting in nothing but them), approximated the same way, like in the designer
        if (cachedFixture.getChannels().stream().anyMatch(cachedChannel -> cachedChannel.getColorWheel() != null)) {
            return;
        }

        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
            if (cachedChannel.getChannel() != null) {
                CachedFixtureCapability capability = getApproximatedColorPresetCapability(state.getFixtureCapabilityValues(), cachedChannel);

                if (capability != null) {
                    FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                    fixtureChannelValue.setChannelName(cachedChannel.getName());
                    fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                    fixtureChannelValue.setValue(capability.getCenterValue());
                    this.mixChannelValue(values, fixtureChannelValue, 1);
                }
            }
        }
    }

    private void mixChannelValues(PresetStepState state, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage) {
        // mix the channel values of the state the preset is in
        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
            if (cachedChannel.getChannel() != null) {
                for (FixtureChannelValue channelValue : state.getFixtureChannelValues()) {
                    if (cachedFixture.getProfile().getUuid().equals(channelValue.getProfileUuid()) && cachedChannel.getName().equals(channelValue.getChannelName())) {
                        this.mixChannelValue(values, channelValue, intensityPercentage);
                    }
                }
            }
        }
    }

    // return all fixture uuids with their corresponding channel values
    private Map<CachedFixture, List<FixtureChannelValue>> getChannelValues(double timeMillis, List<PresetRegionScene> presets) {
        // Loop over all relevant presets and calc the property values from the presets (capabilities and effects)
        HashMap<CachedFixture, List<FixtureChannelValue>> calculatedFixtures = new HashMap<>();
        frameTimeMillis = timeMillis;
        frameAudio.clear();
        frameSizedSettings.clear();

        if (project == null) {
            logger.trace("No project set. Skip fixture calculation.");
            return calculatedFixtures;
        }

        // the number of chase steps per preset (needed by the effects to spread themselves
        // over all fixtures of their preset)
        Map<Preset, Integer> fixtureCounts = new HashMap<>();

        // which state each preset is in. The same preset can play in more than one region
        // at a time, each of them at a different point of its sequence, so the entry of the
        // playing preset is what this is keyed by.
        Map<PresetRegionScene, PresetStepState> presetStates = new HashMap<>();

        for (PresetRegionScene preset : presets) {
            fixtureCounts.computeIfAbsent(preset.getPreset(), this::getPresetFixtureCount);

            // a preset chasing its steps over its fixtures is at a different point of
            // the sequence for each of them, so its state is worked out per fixture below
            if (!PresetSteps.stepsArePhased(preset.getPreset())) {
                presetStates.put(preset, getPresetStepState(preset, timeMillis, 0));
            }
        }

        // what the fixture pool asks the fixtures to do, in the order the designer plays it
        Map<Maintenance.Action, List<String>> maintenance = new LinkedHashMap<>();
        maintenance.put(Maintenance.Action.RESET, resetFixtureUuids);
        maintenance.put(Maintenance.Action.LAMP_ON, lampOnFixtureUuids);

        // where the fixtures hang, to aim the moving heads at a spot
        Map<String, PanTilt.StagePlacement> placements = PanTilt.getStagePlacements(project);

        for (int i = 0; i < cachedFixtures.size(); i++) {
            CachedFixture cachedFixture = cachedFixtures.get(i);

            CachedFixture alreadyCalculatedFixture = sameLampIndices.length == cachedFixtures.size() && sameLampIndices[i] >= 0 ? cachedFixtures.get(sameLampIndices[i]) : null;

            // all values of the current fixture channels. A fixture on the same DMX address as one before is the same
            // lamp: it takes its values, like in the designer.
            List<FixtureChannelValue> values = alreadyCalculatedFixture == null ? new ArrayList<>() : new ArrayList<>(calculatedFixtures.get(alreadyCalculatedFixture));

            if (logger.isTraceEnabled()) {
                logger.trace("Calculate fixture: " + cachedFixture.getProfile().getName());
            }

            if (alreadyCalculatedFixture == null) {
                // apply the default values
                for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                    if (cachedChannel.getChannel() != null) {
                        if (cachedChannel.getDefaultValue() != null) {
                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                            fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                            fixtureChannelValue.setValue(cachedChannel.getDefaultValue());
                            this.mixChannelValue(values, fixtureChannelValue, 1);
                        }
                    }
                }

                // the effects following what the presets give the fixture, played once all of them are mixed
                List<FixtureChannelValue> defaultValues = new ArrayList<>(values);
                FollowingEffects following = new FollowingEffects();

                for (int layer = 0; layer < presets.size(); layer++) {
                    PresetRegionScene preset = presets.get(layer);
                    // search for this fixture in the preset and get it's preset-specific index (for chasing effects)
                    String pixelKey = cachedFixture.getPixel() != null ? cachedFixture.getPixel().getKey() : null;
                    Integer presetIndex = getFixtureIndex(preset.getPreset(), cachedFixture.getFixture().getUuid(), pixelKey);

                    if (presetIndex != null) {
                        // this fixture is also in the preset -> mix the required values (overwrite existing values,
                        // if set multiple times). Its chases and its fans in its own order follow the pattern of the
                        // preset.
                        Integer presetCount = fixtureCounts.get(preset.getPreset());
                        int fixtureIndex = presetIndex;
                        Integer fixtureCount = presetCount;

                        if (presetCount != null) {
                            SelectionPattern.Place pattern = preset.getPreset().getPatternPlace(presetIndex, presetCount);
                            fixtureIndex = pattern.index();
                            fixtureCount = pattern.count();
                        }

                        double intensityPercentage = preset instanceof LiveScenes.LivePreset livePreset ? livePreset.getIntensity(timeMillis) : getPresetIntensity(preset, timeMillis);
                        PresetStepState state = presetStates.get(preset);

                        if (state == null) {
                            state = getPresetStepState(preset, timeMillis, PresetSteps.getStepsPhasingMillis(preset.getPreset(), fixtureIndex, fixtureCount));
                        }

                        PanTilt.Context context = new PanTilt.Context(fixtureIndex, fixtureCount == null ? 1 : fixtureCount, placements.get(cachedFixture.getFixture().getUuid()), getStageFanPlace(preset.getPreset(), cachedFixture));

                        // what the preset puts on this fixture, the fans worked out for its place in them (see Fan)
                        state = getFixtureState(state, context);

                        mixCapabilityValues(state, cachedFixture, values, intensityPercentage, preset.getPreset(), context);
                        mixChannelValues(state, cachedFixture, values, intensityPercentage);
                        PresetChase chase = getPresetChase(preset.getPreset());
                        Integer wholeFixtureIndex = chase.wholeFixtureIndices().get(cachedFixture.getFixture().getUuid());

                        mixEffects(timeMillis, fixtureIndex, fixtureCount, preset, cachedFixture, values, intensityPercentage, state, context, wholeFixtureIndex == null ? 0 : wholeFixtureIndex, chase.wholeFixtureCount(), following, layer);
                    }
                }

                mixFollowingEffects(following, cachedFixture, values, defaultValues);
            }

            // a fixture being identified from the fixture pool of the designer blinks, whatever the presets light it with
            if (identifyFixtureUuids.contains(cachedFixture.getFixture().getUuid())) {
                values = getIdentifyValues(cachedFixture, values, timeMillis);
            }

            // a fixture asked to reset itself or to switch its lamp on does it, whatever the presets do with it
            for (Map.Entry<Maintenance.Action, List<String>> entry : maintenance.entrySet()) {
                if (entry.getValue().contains(cachedFixture.getFixture().getUuid())) {
                    Maintenance.Found found = Maintenance.getCapability(cachedFixture.getChannels(), entry.getKey());

                    if (found != null && found.capability().getCapability().getDmxRange() != null && found.capability().getCapability().getDmxRange().size() == 2) {
                        values = new ArrayList<>(values);
                        mixFixtureChannelValue(values, cachedFixture, found.channel().getName(), found.capability().getCenterValue());
                    }
                }
            }

            // Store the calculated values for subsequent fixtures on the same DMX address
            calculatedFixtures.put(cachedFixture, values);
        }

        return calculatedFixtures;
    }

    // the values of a fixture being identified: the ones the presets give it, with what identifies it on top (see
    // Identify). It keeps everything else, e.g. where it points to.
    private List<FixtureChannelValue> getIdentifyValues(CachedFixture cachedFixture, List<FixtureChannelValue> presetValues, double timeMillis) {
        List<FixtureChannelValue> values = new ArrayList<>(presetValues);
        PresetStepState state = new PresetStepState();
        state.setFixtureCapabilityValues(Identify.getCapabilityValues(cachedFixture, timeMillis));
        mixCapabilityValues(state, cachedFixture, values, 1, new Preset(), new PanTilt.Context(0, 1, null));

        return values;
    }

    // the tempo the curves synced to the beat follow: the one of the composition playing, or else the one of the
    // composition open in the designer, like its own preview. Without either, the curves run on their default tempo.
    private Double getBeatsPerMinute() {
        Composition tempoComposition = getTempoComposition();
        return tempoComposition == null ? null : tempoComposition.getBeatsPerMinute();
    }

    private Composition getTempoComposition() {
        Project tempoProject = project;

        if (!playPreview && compositionPlayer == null) {
            return tempoProject == null ? null : getCompositionByUuid(tempoProject, tempoProject.getSelectedCompositionUuid());
        }

        return composition;
    }

    // ---- the tempo ----

    @Override
    public BeatClock getBeatClock() {
        return beatClock;
    }

    // the tempo from outside: tapped, set or of a MIDI clock (null = the programmed one, see TempoService)
    @Override
    public void setOutsideBeatsPerMinute(Double beatsPerMinute) {
        outsideBeatsPerMinute = beatsPerMinute;
        syncBeatClock();
    }

    @Override
    public Double getOutsideBeatsPerMinute() {
        return outsideBeatsPerMinute;
    }

    // the tempo of the composition or the scenes playing (null while nothing plays)
    @Override
    public Double getProgrammedBeatsPerMinute() {
        if (project == null || (compositionPlayer == null && !playingManualScenes)) {
            return null;
        }

        return getBeatsPerMinute();
    }

    // the tempo playing: the one from outside, else the programmed one (null while neither is)
    @Override
    public Double getTempoBeatsPerMinute() {
        Double outside = outsideBeatsPerMinute;
        return outside != null ? outside : getProgrammedBeatsPerMinute();
    }

    // the beats of a bar of the composition or the scenes playing (4, if nothing plays)
    @Override
    public int getBeatsPerBar() {
        Composition tempoComposition = project == null ? null : getTempoComposition();
        return tempoComposition == null ? 4 : tempoComposition.getTimeSignatureUpper();
    }

    // the beat clock runs at the tempo playing (on at the last one, while none plays)
    private void syncBeatClock() {
        Double beatsPerMinute = getTempoBeatsPerMinute();

        if (beatsPerMinute != null && beatsPerMinute > 0) {
            beatClock.setBeatsPerMinute(beatsPerMinute, System.currentTimeMillis());
        }
    }

    // ---- the holds of the timeline ----

    // The timeline of the composition playing holds at the passed hold point until GO (see endHold): the position stands
    // still, the clips playing run on, the time held counting for them (see CompositionHold).
    @Override
    public synchronized void hold(double positionMillis) {
        List<CompositionHold> next = new ArrayList<>(holds);
        next.add(new CompositionHold(positionMillis, 0));
        holdStartRealMillis = System.currentTimeMillis();
        holds = next;
        holding = true;
    }

    // GO: the timeline goes on from the hold point
    @Override
    public synchronized void endHold() {
        if (!holding) {
            return;
        }

        updateHold();
        holding = false;
    }

    @Override
    public boolean isHolding() {
        return holding;
    }

    // the position jumped to the passed one: the holds from there on weren't gone through anymore
    @Override
    public synchronized void forgetHolds(double fromPositionMillis) {
        List<CompositionHold> next = new ArrayList<>();

        for (CompositionHold hold : holds) {
            if (hold.getPositionMillis() < fromPositionMillis) {
                next.add(hold);
            }
        }

        holds = next;
        holding = false;
    }

    @Override
    public List<Double> getHoldPositions(String compositionName) {
        Project holdProject = compositionName == null ? null : getProjectByCompositionName(compositionName);
        Composition holdComposition = holdProject == null ? null : getCompositionByName(holdProject, compositionName);
        List<Double> positions = new ArrayList<>();

        if (holdComposition == null) {
            return positions;
        }

        for (CompositionMarker marker : holdComposition.getMarkers()) {
            if (marker.isHold()) {
                positions.add(marker.getPositionMillis());
            }
        }

        Collections.sort(positions);
        return positions;
    }

    @Override
    public synchronized void setPreviewHolds(List<CompositionHold> previewHolds, boolean previewHolding) {
        List<CompositionHold> next = new ArrayList<>();

        if (previewHolds != null) {
            for (CompositionHold hold : previewHolds) {
                next.add(new CompositionHold(hold.getPositionMillis(), hold.getHeldMillis()));
            }
        }

        holds = next;
        holding = previewHolding && !next.isEmpty();

        if (holding) {
            // held for as long already
            holdStartRealMillis = System.currentTimeMillis() - next.getLast().getHeldMillis();
        }
    }

    // the hold going on lasts until now
    private void updateHold() {
        List<CompositionHold> current = holds;

        if (holding && !current.isEmpty()) {
            current.getLast().setHeldMillis(System.currentTimeMillis() - holdStartRealMillis);
        }
    }

    // The time a preset of a clip has played for in beats, as the milliseconds they last at the passed tempo, where what
    // follows the beat runs on the beats of the tempo playing instead of the time played: for the scenes picked by hand,
    // and for a composition following the tempo from outside (see isFollowingOutsideTempo). Null elsewhere.
    private Double getBeatTimeMillis(PresetRegionScene preset, double beatsPerMinute) {
        if (preset instanceof LiveScenes.LivePreset livePreset) {
            return livePreset.getBeatTimeMillis(beatsPerMinute);
        }

        if (preset.getRegion() == null) {
            return null;
        }

        Double startBeats = regionStartBeats.get(preset.getRegion());

        if (startBeats == null) {
            return null;
        }

        return (frameBeats - startBeats) * 60000 / beatsPerMinute;
    }

    // Whether what follows the beat in the passed preset runs on the beat clock (see getBeatTimeMillis): at the tempo
    // playing then, so a time in milliseconds (e.g. a phasing) lasts as long as it does
    private boolean isOnTheBeatClock(PresetRegionScene preset) {
        return preset instanceof LiveScenes.LivePreset || (preset.getRegion() != null && regionStartBeats.containsKey(preset.getRegion()));
    }

    // the tempo the curves of the passed preset follow
    private Double getCurveBeatsPerMinute(PresetRegionScene preset) {
        return isOnTheBeatClock(preset) ? (Double) beatClock.getBeatsPerMinute() : getBeatsPerMinute();
    }

    // Whether the composition playing follows the tempo from outside: one without audio to follow, or the default
    // composition played in between, while a tempo is tapped, set or given by a MIDI clock. A composition with audio
    // keeps its own tempo: the lights stay in time with the music.
    private boolean isFollowingOutsideTempo() {
        CompositionPlayer player = compositionPlayer;
        Composition playing = composition;

        return outsideBeatsPerMinute != null && !playPreview && player != null && playing != null
                && (player.isDefaultComposition() || "none".equals(playing.getSyncType()));
    }

    // the clips of the composition playing at the moment: those following the tempo from outside start on the beat of
    // it they began on (a whole one), the others forget theirs
    private void updateRegionStartBeats(List<PresetRegionScene> presets, double timeMillis) {
        frameBeats = beatClock.getBeats(System.currentTimeMillis());

        if (!isFollowingOutsideTempo()) {
            regionStartBeats.clear();
            return;
        }

        double beatsPerMinute = getStepsBeatsPerMinute();
        Set<ScenePlaybackRegion> playing = Collections.newSetFromMap(new IdentityHashMap<>());

        for (PresetRegionScene preset : presets) {
            ScenePlaybackRegion region = preset.getRegion();

            if (region == null) {
                continue;
            }

            playing.add(region);

            // a clip joined after it began started as many beats ago
            regionStartBeats.computeIfAbsent(region, started -> Math.floor(frameBeats - Math.max(0, timeMillis - started.getStartMillis()) * beatsPerMinute / 60000 + 1e-9));
        }

        regionStartBeats.keySet().retainAll(playing);
    }

    // the tempo the steps following the beat are played at: the one the curves follow, or else their default one, like in
    // the designer
    private double getStepsBeatsPerMinute() {
        Double beatsPerMinute = getBeatsPerMinute();
        return beatsPerMinute == null ? EffectCurve.DEFAULT_BEATS_PER_MINUTE : beatsPerMinute;
    }

    // a preset that is not placed in a composition has no moment it starts at: the preview
    // clock has been running since the designer was opened, so a curve that stops after a
    // while would always be over already. repeat its run instead, the way the grid of the
    // effect shows it.
    private double getCurveTimeMillis(EffectCurve curve, double effectTimeMillis, PresetRegionScene preset, Integer fixtureCount, Double beatsPerMinute) {
        if (preset.getRegion() != null) {
            if (curve.isBeatSynced()) {
                // a curve following the beat runs on the beats of the tempo from outside, where it does
                Double beatTimeMillis = getBeatTimeMillis(preset, beatsPerMinute != null && beatsPerMinute > 0 ? beatsPerMinute : EffectCurve.DEFAULT_BEATS_PER_MINUTE);

                if (beatTimeMillis != null) {
                    return beatTimeMillis;
                }
            }

            return effectTimeMillis;
        }

        Double loopMillis = curve.getRunLoopMillis(fixtureCount, beatsPerMinute);

        if (loopMillis == null) {
            return effectTimeMillis;
        }

        return (effectTimeMillis % loopMillis + loopMillis) % loopMillis;
    }

    // An effect following what the presets give a fixture, played once all of them are mixed (see
    // mixFollowingEffects): a movement, a curve on the pan, the tilt or the dimmer (its capabilities following the
    // presets), or a pixel effect in the colors of the preset or blended with them
    private record FollowingEffect(Effect effect, List<FixtureCapability> capabilities, double effectTimeMillis, PresetRegionScene preset, double intensityPercentage, PanTilt.Context context, int fixtureIndex, Integer fixtureCount, int wholeFixtureIndex, int wholeFixtureCount, int layer) {
    }

    private static final class FollowingEffects {
        private final List<FollowingEffect> effects = new ArrayList<>();

        // the topmost preset playing a pixel effect in colors of its own on the fixture, which covers the pixel effects
        // of the presets below it following the presets
        private int coveringPixelLayer = -1;
    }

    // whether a curve on the capability follows what the presets give a fixture: the pan and the tilt swing around
    // where they point it, the dimmer pulses the brightness they light it with
    private static boolean followsPresets(FixtureCapability capability) {
        return capability.getType() == FixtureCapability.FixtureCapabilityType.Intensity
                || capability.getType() == FixtureCapability.FixtureCapabilityType.Pan
                || capability.getType() == FixtureCapability.FixtureCapabilityType.Tilt;
    }

    // whether a pixel effect follows the color the presets give a pixel: in its shades, or blended with it
    private static boolean pixelFollowsPresets(EffectPixel effect) {
        return "preset".equals(effect.getPalette().getRef()) || !"replace".equals(effect.getBlend());
    }

    private void mixEffects(double timeMillis, int fixtureIndex, Integer fixtureCount, PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage, PresetStepState state, PanTilt.Context context, int wholeFixtureIndex, int wholeFixtureCount, FollowingEffects following, int layer) {
        double effectTimeMillis = timeMillis;

        if (preset.getRegion() != null) {
            // the clips spanning a hold ran on while the timeline held
            double heldMillis = preset instanceof LiveScenes.LivePreset ? 0 : CompositionHold.getHeldMillis(holds, preset.getRegion().getStartMillis(), timeMillis);
            effectTimeMillis = timeMillis - preset.getRegion().getStartMillis() + heldMillis;
        }

        for (Effect effect : preset.getPreset().getEffects()) {
            // the effect keeps running through the steps, they only open or close it
            double effectAmount = state.getEffectAmount(effect.getUuid());
            double effectIntensityPercentage = intensityPercentage * effectAmount;

            if (effect.isVisible() && effectAmount > 0) {
                // a movement runs around where all the presets point the heads, see mixFollowingEffects
                if (effect instanceof EffectPanTilt panTilt) {
                    following.effects.add(new FollowingEffect(panTilt, null, effectTimeMillis, preset, effectIntensityPercentage, context, fixtureIndex, fixtureCount, wholeFixtureIndex, wholeFixtureCount, layer));
                    continue;
                }

                // a pixel effect chases from one fixture to the next, with all of their pixels
                if (effect instanceof EffectPixel pixelEffect) {
                    if (pixelFollowsPresets(pixelEffect)) {
                        following.effects.add(new FollowingEffect(pixelEffect, null, effectTimeMillis, preset, effectIntensityPercentage, context, fixtureIndex, fixtureCount, wholeFixtureIndex, wholeFixtureCount, layer));
                    } else if (mixPixelEffect(pixelEffect, effectTimeMillis, preset, cachedFixture, values, effectIntensityPercentage, wholeFixtureIndex, wholeFixtureCount, values)) {
                        following.coveringPixelLayer = Math.max(following.coveringPixelLayer, layer);
                    }
                    continue;
                }

                // EffectCurve
                if (effect instanceof EffectCurve effectCurve) {
                    Double beatsPerMinute = getCurveBeatsPerMinute(preset);
                    double curveTimeMillis = getCurveTimeMillis(effectCurve, effectTimeMillis, preset, fixtureCount, beatsPerMinute);
                    List<FixtureCapability> followingCapabilities = effectCurve.getCapabilities().stream().filter(DefaultDesignerService::followsPresets).toList();

                    if (!followingCapabilities.isEmpty()) {
                        following.effects.add(new FollowingEffect(effectCurve, followingCapabilities, effectTimeMillis, preset, effectIntensityPercentage, context, fixtureIndex, fixtureCount, wholeFixtureIndex, wholeFixtureCount, layer));
                    }

                    // capabilities, swung as far as the place of the fixture in the fan of the preset says
                    Double curveValue = effectCurve.getFannedValue(effectCurve.getValueAtMillis(curveTimeMillis, fixtureIndex, fixtureCount, beatsPerMinute), Fan.getFanPlace(context));

                    // the curve does not apply anymore after it has finished running
                    if (curveValue != null) {
                        for (FixtureCapability capability : effectCurve.getCapabilities()) {
                            if (followsPresets(capability)) {
                                continue;
                            }

                            FixtureCapabilityValue capabilityValue = effectCurve.getCapabilityValue(capability, curveValue);

                            for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                                Double channelValue = getCurveChannelValue(capabilityValue, cachedChannel, preset.getPreset());

                                if (channelValue != null) {
                                    FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                    fixtureChannelValue.setChannelName(cachedChannel.getName());
                                    fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                    fixtureChannelValue.setValue(channelValue);
                                    mixChannelValue(values, fixtureChannelValue, effectIntensityPercentage);
                                }
                            }
                        }
                    }

                    // channels
                    for (EffectCurveProfileChannels channelProfile : effectCurve.getChannels()) {
                        if (channelProfile.profileUuid.equals(cachedFixture.getProfile().getUuid())) {
                            for (String channel : channelProfile.getChannels()) {
                                for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                                    if (cachedChannel.getName().equals(channel)) {
                                        Double value = effectCurve.getFannedValue(effectCurve.getValueAtMillis(curveTimeMillis, fixtureIndex, fixtureCount, beatsPerMinute), Fan.getFanPlace(context));

                                        // the curve does not apply anymore after it has finished running
                                        if (value != null) {
                                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                                            fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                            fixtureChannelValue.setValue(cachedChannel.getMaxValue() * value);
                                            mixChannelValue(values, fixtureChannelValue, effectIntensityPercentage);
                                        }
                                    }
                                }
                            }

                            break;
                        }
                    }
                }
            }
        }
    }

    // A pixel effect on a part of a fixture (a pixel, or a fixture without pixels): the color of its pattern where the part
    // is in its fixture, blended with the color the preset puts on it, on its red, green and blue. Its other channels keep
    // what the preset puts on them, its dimmer as well. A pixel lighting in white only (see getWhitePixelDimmer) lights as
    // bright as the color is, on its dimmer. This follows the same rules as the designer.
    // The color the presets put on the part is read from the passed base values (all the presets, for an effect
    // following them). Whether the effect lit the part.
    private boolean mixPixelEffect(EffectPixel effect, double effectTimeMillis, PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double effectIntensityPercentage, int fixtureIndex, int fixtureCount, List<FixtureChannelValue> baseValues) {
        CachedFixtureChannel[] channels = {
                getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Red),
                getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Green),
                getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Blue)
        };

        boolean lightsInColors = channels[0] != null || channels[1] != null || channels[2] != null;
        CachedFixtureChannel whiteDimmer = lightsInColors ? null : getWhitePixelDimmer(cachedFixture);

        if (!lightsInColors && whiteDimmer == null) {
            return false;
        }

        Double beatsPerMinute = getCurveBeatsPerMinute(preset);
        double curveTimeMillis = getCurveTimeMillis(effect, effectTimeMillis, preset, fixtureCount, beatsPerMinute);
        // a still pattern stands where its phase and the chase put it
        Double cycles = effect.isStill()
                ? Double.valueOf(effect.getStillCycles(fixtureIndex, fixtureCount, beatsPerMinute))
                : effect.getCyclesAtMillis(curveTimeMillis, fixtureIndex, fixtureCount, beatsPerMinute);

        // the effect does not apply anymore after it has finished running
        if (cycles == null) {
            return false;
        }

        // the color the preset puts on the part, as red, green and blue between 0 and 1 (white at full on a pixel lighting
        // in white only, which its dimmer dims)
        double[] base = {1, 1, 1};

        if (whiteDimmer == null) {
            for (int i = 0; i < 3; i++) {
                FixtureChannelValue value = channels[i] == null ? null : getChannelValue(baseValues, channels[i].getName(), cachedFixture.getProfile().getUuid());
                base[i] = value == null ? 0 : value.getValue() / channels[i].getMaxValue();
            }
        }

        // Where the part is in its fixture, every fixture starting the random looking patterns somewhere else. Spread
        // across the stage, where it is in the picture of the stage, which is one for all of them.
        boolean stage = "stage".equals(effect.getSpread());
        PixelContext context = (stage ? getStagePixelPlace(preset.getPreset(), effect.getView(), cachedFixture) : getPixelPlace(cachedFixture)).copy();
        context.seed = PixelMath.toInt(effect.getSeed() * 7919 + (stage ? 0 : fixtureIndex * 131));

        if (effect.isMirror()) {
            context.x = context.x < 0.5 ? context.x * 2 : (1 - context.x) * 2;
        }

        // The audio it follows: shown by the level meter and the wave of the audio, followed with its brightness or its
        // size by the other patterns
        PixelAudio audio = getPixelAudio(effect);
        double audioLevel = audio != null && !effect.showsAudio() ? audio.level() : 1;
        boolean followsSize = audio != null && !effect.showsAudio() && "size".equals(effect.getAudioFollows());
        PixelPatternSettings settings = followsSize ? getSizedSettings(effect, audioLevel) : effect;

        // the color of the pixel through the palette, or the own one of an image
        double[] sample = new double[5];
        PixelPatterns.sample(settings, cycles, context, audio, sample);

        if (!followsSize) {
            sample[1] = sample[1] * audioLevel;
        }
        PixelPalette palette = effect.getPalette();
        List<PixelPaletteStop> stops = "preset".equals(palette.getRef()) ? PixelPalette.shadesOf(base) : palette.getStops();
        double[] color = sample[2] >= 0 ? new double[]{sample[2], sample[3], sample[4]} : PixelPalette.colorOf(stops, palette.getBetween(), palette.getRepeat(), sample[0]);
        double[] lit = {color[0] * sample[1], color[1] * sample[1], color[2] * sample[1]};

        // A pixel lighting in white only lights as bright as the brightest of the red, green and blue of the light, blended
        // with the white at full the preset puts on it, and dimmed by its dimmer, like a pixel lighting in colors is
        if (whiteDimmer != null) {
            FixtureChannelValue value = getChannelValue(baseValues, whiteDimmer.getName(), cachedFixture.getProfile().getUuid());
            double dimmer = value == null ? 0 : value.getValue() / whiteDimmer.getMaxValue();

            mixPixelValue(effect, cachedFixture, whiteDimmer, 1, Math.max(Math.max(lit[0], lit[1]), lit[2]), sample[1], values, effectIntensityPercentage, dimmer);
            return true;
        }

        // the white part of the light on the white LED, if it is to light it
        CachedFixtureChannel white = effect.isWhiteLed() ? getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.White) : null;
        double litWhite = 0;

        if (white != null) {
            litWhite = Math.min(Math.min(lit[0], lit[1]), lit[2]);

            for (int i = 0; i < 3; i++) {
                lit[i] = lit[i] - litWhite;
            }
        }

        for (int i = 0; i < 3; i++) {
            if (channels[i] != null) {
                mixPixelValue(effect, cachedFixture, channels[i], base[i], lit[i], sample[1], values, effectIntensityPercentage, 1);
            }
        }

        if (white != null) {
            FixtureChannelValue value = getChannelValue(baseValues, white.getName(), cachedFixture.getProfile().getUuid());
            double baseWhite = value == null ? 0 : value.getValue() / white.getMaxValue();

            mixPixelValue(effect, cachedFixture, white, baseWhite, litWhite, sample[1], values, effectIntensityPercentage, 1);
        }

        return true;
    }

    // the audio a pixel effect follows at the moment of the frame, none for one which follows none
    private PixelAudio getPixelAudio(EffectPixel effect) {
        if (!"composition".equals(effect.getAudioSource())) {
            return null;
        }

        return frameAudio.computeIfAbsent(effect, key -> {
            // the buses it follows added together, each frame once in the frame of the lights
            List<String> buses = effect.getAudioBuses();
            Map<Integer, double[]> added = new HashMap<>();

            return new PixelAudio(index -> added.computeIfAbsent(index, known -> audioAnalysisService.frame(buses, known)), frameTimeMillis, effect);
        });
    }

    // the settings of a pixel effect with its size following the audio, once in a frame
    private PixelPatternSettings getSizedSettings(EffectPixel effect, double audioLevel) {
        return frameSizedSettings.computeIfAbsent(effect, key -> new PixelPatternSettings() {
            // the size the audio lets it have
            private final double size = effect.getSize() * audioLevel;

            @Override
            public String getPattern() {
                return effect.getPattern();
            }

            @Override
            public double getSize() {
                return size;
            }

            @Override
            public double getSoftness() {
                return effect.getSoftness();
            }

            @Override
            public String getDirection() {
                return effect.getDirection();
            }

            @Override
            public String getShape() {
                return effect.getShape();
            }

            @Override
            public boolean isBounce() {
                return effect.isBounce();
            }

            @Override
            public PixelBitmap getBitmap() {
                return effect.getBitmap();
            }

            @Override
            public String getScroll() {
                return effect.getScroll();
            }

            @Override
            public String getAlign() {
                return effect.getAlign();
            }

            @Override
            public double getOffsetY() {
                return effect.getOffsetY();
            }

            @Override
            public String getFit() {
                return effect.getFit();
            }

            @Override
            public String getTextColors() {
                return effect.getTextColors();
            }

            @Override
            public String getImageColors() {
                return effect.getImageColors();
            }
        });
    }

    // The audio buses the pixel effects of a project follow: the ones they name, all of them for an effect naming none
    static Set<String> followedBuses(Project project, List<AudioBus> audioBuses) {
        Set<String> followed = new HashSet<>();

        if (project == null || project.getPresets() == null) {
            return followed;
        }

        for (Preset preset : project.getPresets()) {
            for (Effect effect : preset.getEffects() == null ? List.<Effect>of() : preset.getEffects()) {
                if (effect instanceof EffectPixel pixel && "composition".equals(pixel.getAudioSource())) {
                    if (pixel.getAudioBuses().isEmpty()) {
                        for (AudioBus audioBus : audioBuses == null ? List.<AudioBus>of() : audioBuses) {
                            followed.add(audioBus.getUuid());
                        }
                    } else {
                        followed.addAll(pixel.getAudioBuses());
                    }
                }
            }
        }

        return followed;
    }

    // Package visible for the conformance cases of the designer, which play recorded analyses
    AudioAnalysisService getAudioAnalysisService() {
        return audioAnalysisService;
    }

    // A color of a pixel the effect lights, blended with the one the preset puts on it (base, between 0 and 1): the
    // light of the pattern (lit) in place of it, the base as bright as the pattern makes it (mask), the light added to it
    // or the brighter of both. Dimmed (between 0 and 1) on the dimmer of a pixel lighting in white only.
    private void mixPixelValue(EffectPixel effect, CachedFixture cachedFixture, CachedFixtureChannel channel, double base, double lit, double level, List<FixtureChannelValue> values, double effectIntensityPercentage, double dimmer) {
        double target = switch (effect.getBlend()) {
            case "mask" -> base * level;
            case "add" -> Math.min(base + lit, 1);
            case "max" -> Math.max(base, lit);
            default -> lit;
        };

        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
        fixtureChannelValue.setChannelName(channel.getName());
        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
        fixtureChannelValue.setValue(channel.getMaxValue() * target * dimmer);
        mixChannelValue(values, fixtureChannelValue, effectIntensityPercentage);
    }

    // The dimmer of a pixel of a bar or a matrix lighting in white only (no red, green and blue, a dimmer of its own, e.g.
    // a LED of a matrix of white strobes), which a pixel effect lights. None for any other part: a fixture without pixels
    // and the general channels of one with pixels (e.g. its master dimmer) keep what the preset puts on them. The
    // designer finds it the same way.
    private static CachedFixtureChannel getWhitePixelDimmer(CachedFixture cachedFixture) {
        if (cachedFixture.getPixel() == null) {
            return null;
        }

        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
                if (channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.Intensity) {
                    return channel;
                }
            }
        }

        return null;
    }

    // whether a pixel effect lights a part of a fixture: in red, green and blue, or in white only
    private static boolean lightsPixelEffect(CachedFixture cachedFixture) {
        return getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Red) != null
                || getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Green) != null
                || getColorChannel(cachedFixture, FixtureCapability.FixtureCapabilityColor.Blue) != null
                || getWhitePixelDimmer(cachedFixture) != null;
    }

    // The state of a preset on one of its fixtures: the values fanned out over the fixtures worked out for its place in
    // the fan (see Fan), travelling from the ones of the step before for the fixture on its own. The state itself, if
    // nothing is fanned.
    private static PresetStepState getFixtureState(PresetStepState state, PanTilt.Context context) {
        List<FixtureCapabilityValue> capabilityValues = Fan.getFixtureFanValues(state.getFixtureCapabilityValues(), context);

        if (capabilityValues == state.getFixtureCapabilityValues()) {
            return state;
        }

        PresetStepState fixtureState = new PresetStepState();
        fixtureState.setCurrentStep(state.getCurrentStep());
        fixtureState.setFixtureChannelValues(state.getFixtureChannelValues());
        fixtureState.setFixtureCapabilityValues(capabilityValues);
        fixtureState.setEffectAmounts(state.getEffectAmounts());

        return fixtureState;
    }

    // Where a part of a fixture is in the fan of a preset fanning by where its parts are on the stage (see Fan), worked
    // out once for each preset and order: NaN if they are all at the same place, null for a preset fanning in its own
    // order
    private Double getStageFanPlace(Preset preset, CachedFixture cachedFixture) {
        if (!Fan.isStageFanOrder(preset.getFanOrder())) {
            return null;
        }

        Map<CachedFixture, Double> places = stageFanPlaces
                .computeIfAbsent(preset, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(preset.getFanOrder(), order -> calculateStageFanPlaces(preset, order));
        Double place = places.get(cachedFixture);

        return place == null ? Double.NaN : place;
    }

    private Map<CachedFixture, Double> calculateStageFanPlaces(Preset preset, String order) {
        Map<PresetFixtureKey, Integer> indices = getPresetChase(preset).fixtureIndices();
        Map<String, PanTilt.StagePlacement> placements = PanTilt.getStagePlacements(project);
        List<CachedFixture> parts = new ArrayList<>();
        List<double[]> positions = new ArrayList<>();

        for (CachedFixture part : cachedFixtures) {
            String pixelKey = part.getPixel() == null ? null : part.getPixel().getKey();

            if (!indices.containsKey(new PresetFixtureKey(part.getFixture().getUuid(), pixelKey))) {
                continue;
            }

            parts.add(part);
            positions.add(getPartStagePosition(part, placements));
        }

        List<Double> fanPlaces = Fan.getStageFanPlaces(positions, order);
        Map<CachedFixture, Double> places = new HashMap<>();

        for (int i = 0; i < parts.size(); i++) {
            places.put(parts.get(i), fanPlaces.get(i));
        }

        return places;
    }

    // where a part of a fixture is on the stage (in cm): its LED on the front of the housing of the fixture, the middle
    // of the front of a fixture without pixels (see PixelStage)
    private double[] getPartStagePosition(CachedFixture part, Map<String, PanTilt.StagePlacement> placements) {
        PixelContext place = getPixelPlace(part);
        PanTilt.StagePlacement placement = placements.getOrDefault(part.getFixture().getUuid(), new PanTilt.StagePlacement(new double[]{0, 0, 0}, new double[]{0, 0, 0}));

        return PixelStage.position(place, PixelStage.housingSize(part.getProfile(), place.columns, place.rows), placement, part.getFixture().getPositioning());
    }

    // Where a part of a fixture is in the picture of the stage the parts of a preset make up (the ones a pixel effect
    // lights, see PixelStage), seen from the front or from above. Worked out once for each preset and view.
    private PixelContext getStagePixelPlace(Preset preset, String view, CachedFixture cachedFixture) {
        Map<CachedFixture, PixelContext> places = stagePixelPlaces
                .computeIfAbsent(preset, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(view, key -> calculateStagePixelPlaces(preset, view));
        PixelContext place = places.get(cachedFixture);

        return place == null ? getPixelPlace(cachedFixture) : place;
    }

    private Map<CachedFixture, PixelContext> calculateStagePixelPlaces(Preset preset, String view) {
        Map<PresetFixtureKey, Integer> indices = getPresetChase(preset).fixtureIndices();
        Map<String, PanTilt.StagePlacement> placements = PanTilt.getStagePlacements(project);
        List<CachedFixture> parts = new ArrayList<>();
        List<double[]> positions = new ArrayList<>();

        for (CachedFixture part : cachedFixtures) {
            String pixelKey = part.getPixel() == null ? null : part.getPixel().getKey();

            if (!indices.containsKey(new PresetFixtureKey(part.getFixture().getUuid(), pixelKey)) || !lightsPixelEffect(part)) {
                continue;
            }

            parts.add(part);
            positions.add(getPartStagePosition(part, placements));
        }

        List<PixelContext> contexts = PixelStage.places(positions, view);
        Map<CachedFixture, PixelContext> places = new HashMap<>();

        for (int i = 0; i < parts.size(); i++) {
            places.put(parts.get(i), contexts.get(i));
        }

        return places;
    }

    // the channel of a part of a fixture which lights in a color, the first one if there are several
    private static CachedFixtureChannel getColorChannel(CachedFixture cachedFixture, FixtureCapability.FixtureCapabilityColor color) {
        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
                if (channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity && channelCapability.getCapability().getColor() == color) {
                    return channel;
                }
            }
        }

        return null;
    }

    // Where a part of a fixture is in it, for the pixel effects: a pixel in its column and row of the matrix (a bar has
    // one row), a pixel group in the middle of its pixels, and a part without a pixel (a fixture without pixels, or the
    // general channels of one with pixels) as the one pixel of the fixture. The designer places them the very same way.
    PixelContext getPixelPlace(CachedFixture cachedFixture) {
        if (cachedFixture.getPixelPlace() != null) {
            return cachedFixture.getPixelPlace();
        }

        PixelContext place = new PixelContext();
        CachedFixturePixel pixel = cachedFixture.getPixel();
        FixtureProfile profile = cachedFixture.getProfile();

        if (pixel != null && profile.getMatrix() != null) {
            int[] size = getMatrixSize(profile.getMatrix());
            int columns = Math.max(size[0] == 0 ? 1 : size[0], 1);
            int rows = Math.max(size[1] == 0 ? 1 : size[1], 1);
            double column = (columns - 1) / 2.0;
            double row = (rows - 1) / 2.0;

            if (pixel.getX() > 0) {
                column = pixel.getX() - 1;
                row = pixel.getY() - 1;
            } else {
                List<CachedFixturePixel> groupPixels = fixturePixelGroupResolveService.getPixelsForKeyOrGroup(profile.getMatrix(), getAllPixelKeys(profile), pixel.getKey());

                if (!groupPixels.isEmpty()) {
                    double columnSum = 0;
                    double rowSum = 0;

                    for (CachedFixturePixel groupPixel : groupPixels) {
                        columnSum += groupPixel.getX() - 1;
                        rowSum += groupPixel.getY() - 1;
                    }

                    column = columnSum / groupPixels.size();
                    row = rowSum / groupPixels.size();
                }
            }

            int longest = Math.max(columns, rows);

            place.x = (column + 0.5) / columns;
            place.y = (row + 0.5) / rows;
            place.column = (int) Math.floor(column);
            place.row = (int) Math.floor(row);
            place.columns = columns;
            place.rows = rows;
            place.aspectX = (double) columns / longest;
            place.aspectY = (double) rows / longest;
        }

        cachedFixture.setPixelPlace(place);

        return place;
    }

    // the number of pixels a matrix has on X and Y (0 where it doesn't say), like the designer counts them
    private static int[] getMatrixSize(FixtureMatrix matrix) {
        if (matrix.getPixelKeys() != null) {
            int rows = 0;
            int columns = 0;

            for (List<List<String>> layer : matrix.getPixelKeys()) {
                rows = Math.max(rows, layer.size());

                for (List<String> row : layer) {
                    columns = Math.max(columns, row.size());
                }
            }

            return new int[]{columns, rows};
        }

        List<Integer> pixelCount = matrix.getPixelCount();
        int columns = pixelCount != null && !pixelCount.isEmpty() && pixelCount.get(0) != null ? pixelCount.get(0) : 0;
        int rows = pixelCount != null && pixelCount.size() > 1 && pixelCount.get(1) != null ? pixelCount.get(1) : 0;

        return new int[]{columns, rows};
    }

    // The effects following what the presets give a fixture, whichever preset gives it: the one of the effect, one below
    // it or one above it. They are mixed in the order of their presets, the one on top over the others, like in the
    // designer.
    // - A movement and a curve on the pan or the tilt swing the heads around where the presets point them (the default
    //   of the channel without a preset), each mirrored with its preset.
    // - A curve on the dimmer pulses the brightness the presets light the fixture with: from none at 0 to all of it at
    //   1. Without a preset setting the dimmer, the curve lights it on its own, from 0 to full.
    // - A pixel effect in the colors of its preset, or blended with them, takes the color all the presets put on a
    //   pixel, unless a pixel effect in colors of its own of a preset above covers it.
    private void mixFollowingEffects(FollowingEffects following, CachedFixture cachedFixture, List<FixtureChannelValue> values, List<FixtureChannelValue> defaultValues) {
        if (following.effects.isEmpty()) {
            return;
        }

        // what the presets give the fixture, before the effects following them
        List<FixtureChannelValue> presetValues = new ArrayList<>(values);
        String profileUuid = cachedFixture.getProfile().getUuid();

        // where the presets point the fixture on each axis
        record Axis(CachedFixtureChannel cachedChannel, FixtureCapability.FixtureCapabilityType type, double range, double center) {
        }

        List<Axis> axes = new ArrayList<>();

        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
            if (cachedChannel.getChannel() == null) {
                continue;
            }

            for (FixtureCapability.FixtureCapabilityType type : List.of(FixtureCapability.FixtureCapabilityType.Pan, FixtureCapability.FixtureCapabilityType.Tilt)) {
                Double range = PanTilt.getAxisRange(cachedChannel, type);

                if (range == null) {
                    continue;
                }

                FixtureChannelValue value = getChannelValue(presetValues, cachedChannel.getName(), profileUuid);
                axes.add(new Axis(cachedChannel, type, range, value == null ? 0 : PanTilt.getAxisDegrees(cachedChannel, range, value.getValue())));
            }
        }

        // how bright the presets light the dimmer: full, if none of them sets it (it still has its default value)
        CachedFixtureChannel dimmer = getDimmerChannel(cachedFixture);
        FixtureChannelValue dimmerValue = dimmer == null ? null : getChannelValue(presetValues, dimmer.getName(), profileUuid);
        double brightness = dimmerValue == null || dimmerValue == getChannelValue(defaultValues, dimmer.getName(), profileUuid)
                ? 1
                : getDimmerBrightness(cachedFixture, presetValues);

        for (FollowingEffect item : following.effects) {
            PanTilt.Context context = item.context();

            if (item.effect() instanceof EffectPixel pixelEffect) {
                if (item.layer() >= following.coveringPixelLayer) {
                    mixPixelEffect(pixelEffect, item.effectTimeMillis(), item.preset(), cachedFixture, values, item.intensityPercentage(), item.wholeFixtureIndex(), item.wholeFixtureCount(), presetValues);
                }

                continue;
            }

            Double beatsPerMinute = getCurveBeatsPerMinute(item.preset());

            if (item.effect() instanceof EffectPanTilt movement) {
                double curveTimeMillis = getCurveTimeMillis(movement, item.effectTimeMillis(), item.preset(), context.fixtureCount(), beatsPerMinute);
                EffectPanTilt.Offset offset = movement.getOffsetAtMillis(curveTimeMillis, context.fixtureIndex(), context.fixtureCount(), beatsPerMinute, context.fanPlace());

                // the movement does not apply anymore after it has finished running
                if (offset == null) {
                    continue;
                }

                for (Axis axis : axes) {
                    boolean mirror = item.preset().getPreset().mirrorsCapability(axis.type());
                    double move = axis.type() == FixtureCapability.FixtureCapabilityType.Pan ? offset.pan() : offset.tilt();
                    mixAxisValue(values, cachedFixture, axis.cachedChannel(), axis.range(), axis.center() + (mirror ? -move : move), item.intensityPercentage());
                }

                continue;
            }

            // a curve, swung as far as the place of the fixture in the fan of the preset says
            EffectCurve curve = (EffectCurve) item.effect();
            double curveTimeMillis = getCurveTimeMillis(curve, item.effectTimeMillis(), item.preset(), item.fixtureCount(), beatsPerMinute);
            Double curveValue = curve.getFannedValue(curve.getValueAtMillis(curveTimeMillis, item.fixtureIndex(), item.fixtureCount(), beatsPerMinute), Fan.getFanPlace(context));

            // the curve does not apply anymore after it has finished running
            if (curveValue == null) {
                continue;
            }

            for (FixtureCapability capability : item.capabilities()) {
                if (capability.getType() == FixtureCapability.FixtureCapabilityType.Intensity) {
                    FixtureCapabilityValue capabilityValue = curve.getCapabilityValue(capability, curveValue * brightness);

                    for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                        Double channelValue = getCurveChannelValue(capabilityValue, cachedChannel, item.preset().getPreset());

                        if (channelValue != null) {
                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                            fixtureChannelValue.setProfileUuid(profileUuid);
                            fixtureChannelValue.setValue(channelValue);
                            mixChannelValue(values, fixtureChannelValue, item.intensityPercentage());
                        }
                    }

                    continue;
                }

                // the pan or the tilt: as far from where the presets point the heads as the curve says from the middle
                FixtureCapabilityValue capabilityValue = curve.getCapabilityValue(capability, curveValue);
                boolean mirror = item.preset().getPreset().mirrorsCapability(capability.getType());

                for (Axis axis : axes) {
                    if (axis.type() == capability.getType()) {
                        Double degrees = PanTilt.getPanTiltDegrees(capabilityValue, axis.range(), mirror, null);

                        if (degrees != null) {
                            mixAxisValue(values, cachedFixture, axis.cachedChannel(), axis.range(), axis.center() + degrees, item.intensityPercentage());
                        }
                    }
                }
            }
        }
    }

    // points a fixture at the passed degrees on the axis of a channel, mixed over what it points at with the intensity
    private void mixAxisValue(List<FixtureChannelValue> values, CachedFixture cachedFixture, CachedFixtureChannel cachedChannel, double range, double degrees, double intensityPercentage) {
        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
        fixtureChannelValue.setChannelName(cachedChannel.getName());
        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
        fixtureChannelValue.setValue(PanTilt.getAxisChannelValue(cachedChannel, range, degrees));
        mixChannelValue(values, fixtureChannelValue, intensityPercentage);
    }

    // the first channel of a part of a fixture with a dimmer on it, none without (like getDimmerBrightness)
    private static CachedFixtureChannel getDimmerChannel(CachedFixture cachedFixture) {
        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability capability : channel.getCapabilities()) {
                if (capability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.Intensity) {
                    return channel;
                }
            }
        }

        return null;
    }

    // the position of a channel name inside the fine channel aliases of a cached channel
    // (-1 for the coarse channel itself, 0 for the first fine channel, 1 for the second, ...)
    private int getFineIndexByChannelName(CachedFixtureChannel channel, String channelName) {
        // fine channel aliases of template channels still contain the pixel key placeholder
        // -> resolve them with the pixel key of the cached channel before comparing
        List<String> fineChannelAliases = channel.getChannel().getFineChannelAliases();

        for (int i = 0; i < fineChannelAliases.size(); i++) {
            if (getChannelNameWithPixelKey(fineChannelAliases.get(i), channel.getPixelKey()).equals(channelName)) {
                return i;
            }
        }

        return -1;
    }

    private CachedFixtureChannel getChannelByName(CachedFixture fixture, String channelName) {
        for (CachedFixtureChannel channel : fixture.getChannels()) {
            if ((channel.getName() != null && channel.getName().equals(channelName)) || (channel.getChannel() != null && getFineIndexByChannelName(channel, channelName) > -1)) {
                return channel;
            }
        }

        return null;
    }

    // the DMX address of a channel of the mode of a part, if it is a channel of the part (fine channels are not cached
    // separately: the coarse channel is found for them, the byte they are of it)
    private void addOutputChannel(List<OutputChannel> outputs, int channelIndex, CachedFixture cachedFixture, String templateChannelName, String pixelKey) {
        String channelName = getChannelNameWithPixelKey(templateChannelName, pixelKey);
        CachedFixtureChannel channel = getChannelByName(cachedFixture, channelName);

        if (channel != null && channel.getChannel() != null) {
            outputs.add(new OutputChannel(channelIndex, channel, getFineIndexByChannelName(channel, channelName)));
        }
    }

    // where a part of a fixture sends its channels, in the order of the channels of its mode (see OutputChannel)
    private List<OutputChannel> getOutputChannels(CachedFixture cachedFixture) {
        return outputChannels.computeIfAbsent(cachedFixture, this::calculateOutputChannels);
    }

    private List<OutputChannel> calculateOutputChannels(CachedFixture cachedFixture) {
        List<OutputChannel> outputs = new ArrayList<>();
        int channelIndex = cachedFixture.getFixture().getDmxFirstChannel();

        for (FixtureModeChannel fixtureModeChannel : cachedFixture.getMode().getChannels()) {
            if (isUnusedChannel(fixtureModeChannel)) {
                // an unused channel ("null" in the fixture library) still takes its DMX address
                channelIndex++;
            } else if (fixtureModeChannel.getName() == null || fixtureModeChannel.getName().isEmpty()) {
                // reference a channel through a pixel matrix
                List<CachedFixturePixel> availablePixels = getPixelsInOrder(cachedFixture.getProfile(), fixtureModeChannel.getRepeatFor());

                if (CHANNEL_ORDER_PER_PIXEL.equals(fixtureModeChannel.getChannelOrder())) {
                    // each channel for each pixel
                    for (CachedFixturePixel availablePixel : availablePixels) {
                        for (String modeTemplateChannelName : fixtureModeChannel.getTemplateChannels()) {
                            addOutputChannel(outputs, channelIndex, cachedFixture, modeTemplateChannelName, availablePixel.getKey());
                            channelIndex++;
                        }
                    }
                } else if (CHANNEL_ORDER_PER_CHANNEL.equals(fixtureModeChannel.getChannelOrder())) {
                    // each pixel for each channel
                    for (String modeTemplateChannelName : fixtureModeChannel.getTemplateChannels()) {
                        for (CachedFixturePixel availablePixel : availablePixels) {
                            addOutputChannel(outputs, channelIndex, cachedFixture, modeTemplateChannelName, availablePixel.getKey());
                            channelIndex++;
                        }
                    }
                }
            } else {
                // direct reference to a channel
                addOutputChannel(outputs, channelIndex, cachedFixture, fixtureModeChannel.getName(), null);
                channelIndex++;
            }
        }

        return outputs;
    }

    private double getMasterDimmer() {
        if (project == null) {
            return 1;
        }

        // the scenes picked on the Play screen are dimmed by its master dimmer as well, on top of the one of the project
        return project.getMasterDimmerValue() * (playingManualScenes ? manualScenesMasterDimmer : 1);
    }

    // How much the colors of a fixture with a virtual dimmer are dimmed when they are sent: by the virtual dimmer and
    // by the master dimmer, which dims any other dimmer as well. Null for a fixture without a virtual dimmer, its
    // colors are sent as they are.
    private Double getColorDimmer(CachedFixture cachedFixture, List<FixtureChannelValue> values) {
        CachedFixtureChannel virtualDimmer = getChannelByName(cachedFixture, VIRTUAL_DIMMER_CHANNEL_NAME);

        if (virtualDimmer == null) {
            return null;
        }

        FixtureChannelValue value = getChannelValue(values, VIRTUAL_DIMMER_CHANNEL_NAME, cachedFixture.getProfile().getUuid());
        return (value == null ? 0 : getBrightnessOfValue(virtualDimmer, value.getValue())) * getMasterDimmer();
    }

    // The master dimmer of the project dims the brightness, like in the preview of the designer: a value giving the
    // brightness is scaled within the range of its capability (also from bright to off). Colors aren't dimmed, like in
    // the preview, unless the fixture has a virtual dimmer (see getColorDimmer).
    private double applyMasterDimmer(CachedFixtureChannel channel, double value) {
        return dim(channel, value, FixtureCapability.FixtureCapabilityType.Intensity, getMasterDimmer());
    }

    // Scale the brightness a value gives through a capability of the passed type (e.g. a dimmer or a color) by the
    // passed factor, within the range of the capability. Values of other capabilities stay as they are.
    private double dim(CachedFixtureChannel channel, double value, FixtureCapability.FixtureCapabilityType type, double factor) {
        if (factor >= 1 || channel.getCapabilities() == null) {
            return value;
        }

        double maxLumens = getMaxLumens(channel.getCapabilities());

        for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
            FixtureCapability capability = channelCapability.getCapability();

            if (capability.getType() != type) {
                continue;
            }

            List<Integer> dmxRange = capability.getDmxRange();
            // the whole channel or the range of the capability
            double low = dmxRange == null || dmxRange.size() < 2 ? 0 : dmxRange.get(0);
            double high = dmxRange == null || dmxRange.size() < 2 ? channel.getMaxValue() : dmxRange.get(1);

            if (value < low || value > high) {
                continue;
            }

            double[] range = getBrightnessRange(capability, maxLumens);

            if (range[0] == range[1] || high == low) {
                // a single brightness can't be dimmed
                return value;
            }

            double brightness = range[0] + (range[1] - range[0]) * (value - low) / (high - low);
            double dimmedPosition = (brightness * factor - range[0]) / (range[1] - range[0]);
            return low + (high - low) * Math.max(0, Math.min(1, dimmedPosition));
        }

        return value;
    }

    private boolean isUnusedChannel(FixtureModeChannel fixtureModeChannel) {
        return (fixtureModeChannel.getName() == null || fixtureModeChannel.getName().isEmpty())
                && (fixtureModeChannel.getRepeatFor() == null || fixtureModeChannel.getRepeatFor().isEmpty());
    }

    private void setUniverseValues(Map<CachedFixture, List<FixtureChannelValue>> fixtures) {
        logger.trace("Set universe values...");

        // Reset all DMX universes, and find each of them by the uuid it is mapped to (the first one mapped to it)
        Map<String, HashMap<Integer, Integer>> universes = new HashMap<>();

        for (LightingUniverseState universe : lightingUniverses) {
            universe.reset();
            universes.putIfAbsent(universe.getMappingUuid(), universe.getUniverse());
        }

        // loop over each fixture with its values for each channel
        for (Map.Entry<CachedFixture, List<FixtureChannelValue>> entry : fixtures.entrySet()) {
            HashMap<Integer, Integer> universe = universes.get(entry.getKey().getFixture().getDmxUniverseUuid());

            if (universe == null) {
                continue;
            }

            Double colorDimmer = getColorDimmer(entry.getKey(), entry.getValue());

            for (OutputChannel output : getOutputChannels(entry.getKey())) {
                CachedFixtureChannel channel = output.channel();

                // match this mode channel with the value of its coarse channel
                for (FixtureChannelValue channelValue : entry.getValue()) {
                    if (channel.getName().equals(channelValue.getChannelName())) {
                        double value = applyMasterDimmer(channel, channelValue.getValue());

                        if (colorDimmer != null) {
                            value = dim(channel, value, FixtureCapability.FixtureCapabilityType.ColorIntensity, colorDimmer);
                        }

                        int dmxValue = (int) Math.floor(value / Math.pow(256, channel.getChannel().getFineChannelAliases().size() - (output.fineIndex() + 1))) % 256;
                        universe.put(output.address(), dmxValue);
                        break;
                    }
                }
            }
        }

        // a channel held by a fader of the designer sends the value of the fader, the master dimmer included
        for (DmxOverride dmxOverride : dmxOverrides) {
            HashMap<Integer, Integer> universe = universes.get(dmxOverride.getUniverseUuid());

            if (universe != null && dmxOverride.getDmxChannel() >= 0 && dmxOverride.getDmxChannel() < 512) {
                universe.put(dmxOverride.getDmxChannel(), Math.max(0, Math.min(255, dmxOverride.getValue())));
            }
        }
    }

    // Package visible for the conformance cases of the designer: the values of each fixture (and pixel) of the loaded
    // project at the passed time, the way the universe is worked out (the designer's clock isn't on whole milliseconds)
    Map<CachedFixture, List<FixtureChannelValue>> calculateChannelValues(double timeMillis) {
        return getChannelValues(timeMillis, getPresets(timeMillis));
    }

    // Package visible for the conformance cases of the designer
    List<CachedFixture> getCachedFixtures() {
        return cachedFixtures;
    }

    private void calculateUniverse(double timeMillis) {
        try {
            syncBeatClock();
            updateHold();

            List<PresetRegionScene> presets = getPresets(timeMillis);
            Map<CachedFixture, List<FixtureChannelValue>> calculatedFixtures = getChannelValues(timeMillis, presets);

            setUniverseValues(calculatedFixtures);
        } catch (Exception e) {
            logger.error("Could not calculate the universe", e);
        }
    }

    private double getCurrentPositionMillis() {
        if (compositionPlayer != null) {
            // We have a compositionPlayer -> use it to sync
            return compositionPlayer.getPositionMillis();
        }

        if (playingManualScenes) {
            // the scenes picked by hand run on a clock of their own, as fast as the speed master and the tempo make it
            return liveScenes.advance(System.currentTimeMillis());
        }

        if (holding) {
            // the timeline of the designer holds at the position it is previewed from
            return startPositionMillis;
        }

        // No compositionPlayer, e.g. only designer preview -> just use the current time as reference
        return System.currentTimeMillis() - lastPlayTimeMillis + startPositionMillis;
    }

    private void startTimer() {
        if (universeSenderHandle != null || settingsService.getSettings().getDesignerFrequencyHertz() == null) {
            logger.debug("Could not start preview timer (universeSenderHandle = {}, designerFrequencyHertz = {})", universeSenderHandle, settingsService.getSettings().getDesignerFrequencyHertz());
            return;
        }

        logger.debug("Start designer universe send timer...");

        lastPlayTimeMillis = System.currentTimeMillis();

        final Runnable universeSender = () -> {
            synchronized (universeSendLock) {
                try {
                    calculateUniverse(getCurrentPositionMillis());
                    lightingService.sendExternalSync(settingsService.getSettings().getEnableMonitor());

                    // the scenes let go have faded out: their lights are turned off (outside this send, which the
                    // stop waits for)
                    if (releasingManualScenes && playingManualScenes && liveScenes.isIdle(System.currentTimeMillis())) {
                        releasingManualScenes = false;
                        scheduler.execute(this::stopReleasedManualScenes);
                    }
                } catch (Exception e) {
                    logger.error("Could not send universe", e);
                }
            }
        };

        logger.debug("Designer universe send timer triggering each {} milliseconds", 1000 / settingsService.getSettings().getDesignerFrequencyHertz());

        long periodMillis = 1000 / settingsService.getSettings().getDesignerFrequencyHertz();
        universeSenderHandle = scheduler.scheduleAtFixedRate(universeSender, periodMillis, periodMillis, MILLISECONDS);
    }

    private void stopTimer() {
        logger.debug("Stop universe send timer...");

        if (universeSenderHandle == null) {
            return;
        }

        universeSenderHandle.cancel(false);
        universeSenderHandle = null;

        // A send running at the moment finishes first, e.g. before closing removes the universes it sends
        synchronized (universeSendLock) {
            logger.debug("Universe send timer stopped");
        }
    }

    private FixtureWheel getColorWheelByChannel(CachedFixtureChannel channel, FixtureProfile profile) {
        for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
            if (channelCapability.isWheelIsColor()) {
                return channelCapability.getWheel();
            }
        }

        return null;
    }

    private String getChannelNameWithPixelKey(String templateChannelName, String pixelKey) {
        if (pixelKey == null || pixelKey.isEmpty()) {
            return templateChannelName;
        }

        return templateChannelName.replace("$pixelKey", pixelKey);
    }

    private void addCachedChannel(List<CachedFixtureChannel> channels, FixtureChannel channel, String
            templateChannelName, String pixelKey, FixtureProfile profile) {

        CachedFixtureChannel cachedFixtureChannel = new CachedFixtureChannel();
        String channelName = getChannelNameWithPixelKey(templateChannelName, pixelKey);

        cachedFixtureChannel.setChannel(channel);
        cachedFixtureChannel.setName(channelName);
        cachedFixtureChannel.setPixelKey(pixelKey);
        cachedFixtureChannel.setCapabilities(getCapabilitiesByChannel(cachedFixtureChannel.getChannel(), channelName, profile));
        Double defaultValue = getDefaultValueByChannel(cachedFixtureChannel.getChannel());
        if (defaultValue != null) {
            cachedFixtureChannel.setDefaultValue(defaultValue);
        }
        cachedFixtureChannel.setMaxValue(getMaxValueByChannel(cachedFixtureChannel.getChannel()));
        cachedFixtureChannel.setColorWheel(getColorWheelByChannel(cachedFixtureChannel, profile));
        channels.add(cachedFixtureChannel);
    }

    private CachedFixturePixel createPixel(String key, int x, int y, int z) {
        CachedFixturePixel pixel = new CachedFixturePixel();
        pixel.setKey(key);
        pixel.setX(x);
        pixel.setY(y);
        pixel.setZ(z);
        return pixel;
    }

    // The key the fixture library gives a pixel of a matrix defined by its pixel count, e.g. "3" in a row of pixels,
    // "(2, 3)" on a plane or "(1, 2, 3)" in a cube. Like the library: on a plane, the first number is X (or else Y)
    // and the last Y (or else Z).
    private static String getDefaultPixelKey(List<Integer> pixelCount, int x, int y, int z) {
        boolean xDefined = pixelCount.get(0) > 1;
        boolean yDefined = pixelCount.get(1) > 1;
        boolean zDefined = pixelCount.get(2) > 1;
        int definedAxes = (xDefined ? 1 : 0) + (yDefined ? 1 : 0) + (zDefined ? 1 : 0);

        return switch (definedAxes) {
            case 2 -> "(" + (xDefined ? x : y) + ", " + (yDefined ? y : z) + ")";
            case 3 -> "(" + x + ", " + y + ", " + z + ")";
            default -> String.valueOf(Math.max(x, Math.max(y, z)));
        };
    }

    // Package visible for the tests
    // All pixels of the matrix at their positions, counted from 1 like in the fixture library. The library gives the
    // pixel keys by Z, Y and X, so the pixels come layer by layer and row by row: X changes fastest.
    List<CachedFixturePixel> getAllPixelKeys(FixtureProfile profile) {
        List<CachedFixturePixel> result = new ArrayList<>();
        FixtureMatrix matrix = profile.getMatrix();

        if (matrix == null) {
            return result;
        }

        if (matrix.getPixelKeys() != null) {
            for (int z = 0; z < matrix.getPixelKeys().size(); z++) {
                List<List<String>> layer = matrix.getPixelKeys().get(z);

                for (int y = 0; y < layer.size(); y++) {
                    List<String> row = layer.get(y);

                    for (int x = 0; x < row.size(); x++) {
                        if (row.get(x) != null) {
                            result.add(createPixel(row.get(x), x + 1, y + 1, z + 1));
                        }
                    }
                }
            }
        } else if (matrix.getPixelCount() != null && matrix.getPixelCount().size() == 3) {
            List<Integer> pixelCount = matrix.getPixelCount();

            for (int z = 1; z <= pixelCount.get(2); z++) {
                for (int y = 1; y <= pixelCount.get(1); y++) {
                    for (int x = 1; x <= pixelCount.get(0); x++) {
                        result.add(createPixel(getDefaultPixelKey(pixelCount, x, y, z), x, y, z));
                    }
                }
            }
        }

        return result;
    }

    private static int getPosition(CachedFixturePixel pixel, char axis) {
        return switch (axis) {
            case 'X' -> pixel.getX();
            case 'Y' -> pixel.getY();
            default -> pixel.getZ();
        };
    }

    // The pixels in the order of the passed axes, e.g. "YXZ": Y changes fastest, Z slowest
    private List<CachedFixturePixel> getPixelsByAxes(List<CachedFixturePixel> pixels, String axes) {
        List<CachedFixturePixel> result = new ArrayList<>(pixels);
        result.sort(Comparator.<CachedFixturePixel>comparingInt(pixel -> getPosition(pixel, axes.charAt(2)))
                .thenComparingInt(pixel -> getPosition(pixel, axes.charAt(1)))
                .thenComparingInt(pixel -> getPosition(pixel, axes.charAt(0))));
        return result;
    }

    private List<String> getPixelGroupNames(FixtureProfile profile) {
        if (profile.getMatrix() == null || profile.getMatrix().getPixelGroups() == null) {
            return List.of();
        }

        return profile.getMatrix().getPixelGroups().stream().map(FixtureMatrixPixelGroup::getName).toList();
    }

    // Package visible for the tests
    // The pixels a channel of the mode is repeated for, in the order of their channels. A pixel group counts as one:
    // its channels (e.g. "Red Inner ring") control all of its pixels at once.
    List<CachedFixturePixel> getPixelsInOrder(FixtureProfile profile, List<String> repeatFor) {
        List<CachedFixturePixel> result = new ArrayList<>();

        if (repeatFor == null || repeatFor.isEmpty() || profile.getMatrix() == null) {
            // a channel of its own, no pixels
            return result;
        }

        List<CachedFixturePixel> allPixels = getAllPixelKeys(profile);
        List<String> groupNames = getPixelGroupNames(profile);

        if (repeatFor.size() == 1 && repeatFor.getFirst().startsWith("eachPixel")) {
            String order = repeatFor.getFirst().substring("eachPixel".length());

            if (order.equals("ABC")) {
                // the pixel keys in alphanumerical order
                result.addAll(allPixels);
                result.sort((pixel1, pixel2) -> DefaultFixturePixelGroupResolveService.compareKeys(pixel1.getKey(), pixel2.getKey()));
            } else if (order.equals("Group")) {
                // the pixel groups in the order of the profile
                for (String groupName : groupNames) {
                    result.add(createPixel(groupName, 0, 0, 0));
                }
            } else if (order.length() == 3 && order.contains("X") && order.contains("Y") && order.contains("Z")) {
                // by position, e.g. eachPixelXYZ
                result.addAll(getPixelsByAxes(allPixels, order));
            }

            return result;
        }

        // pixel keys and pixel groups
        for (String entry : repeatFor) {
            CachedFixturePixel pixel = allPixels.stream().filter(p -> p.getKey().equals(entry)).findFirst().orElse(null);

            if (pixel != null) {
                result.add(pixel);
            } else if (groupNames.contains(entry)) {
                result.add(createPixel(entry, 0, 0, 0));
            }
        }

        return result;
    }

    private List<CachedFixtureChannel> getCachedChannels(FixtureProfile profile, FixtureMode mode, String pixelKey) {
        List<CachedFixtureChannel> channels = new ArrayList<>();

        // the pixels and the pixel groups, which the template channels can be named after (e.g. "Red Master")
        List<String> pixelKeysAndGroups = new ArrayList<>();
        getAllPixelKeys(profile).forEach(pixel -> pixelKeysAndGroups.add(pixel.getKey()));
        pixelKeysAndGroups.addAll(getPixelGroupNames(profile));

        if (mode == null) {
            return channels;
        }

        for (FixtureModeChannel channel : mode.getChannels()) {
            if (!channel.getName().isEmpty() && (pixelKey == null || pixelKey.isEmpty())) {
                // direct reference to a channel
                // don't check the fine channels. only add the coarse channel.
                boolean channelFound = false;

                for (Map.Entry<String, FixtureChannel> entry : profile.getAvailableChannels().getAvailableChannels().entrySet()) {
                    if (channel.getName().equals(entry.getKey())) {
                        addCachedChannel(channels, entry.getValue(), entry.getKey(), null, profile);
                        channelFound = true;
                        break;
                    }
                }

                // check the template channels, if not found in the available channels
                if (!channelFound) {
                    for (Map.Entry<String, FixtureChannel> entry : profile.getTemplateChannels().getTemplateChannels().entrySet()) {
                        for (String existingPixelKey : pixelKeysAndGroups) {
                            String channelName = this.getChannelNameWithPixelKey(entry.getKey(), existingPixelKey);

                            if (channel.getName().equals(channelName)) {
                                this.addCachedChannel(channels, entry.getValue(), channelName, existingPixelKey, profile);
                            }
                        }
                    }
                }
            } else {
                // reference a channel through a pixel matrix
                List<CachedFixturePixel> availablePixelKeys = getPixelsInOrder(profile, channel.getRepeatFor());

                if (CHANNEL_ORDER_PER_PIXEL.equals(channel.getChannelOrder())) {
                    // each channel for each pixel

                    for (CachedFixturePixel availablePixelKey : availablePixelKeys) {
                        if (availablePixelKey.getKey().equals(pixelKey)) {
                            for (String modeTemplateChannelName : channel.getTemplateChannels()) {
                                for (Map.Entry<String, FixtureChannel> entry : profile.getTemplateChannels().getTemplateChannels().entrySet()) {
                                    // don't check the fine channels. only add the coarse channel.
                                    if (modeTemplateChannelName.equals(entry.getKey())) {
                                        this.addCachedChannel(channels, entry.getValue(), entry.getKey(), availablePixelKey.getKey(), profile);
                                    }
                                }
                            }
                        }
                    }
                } else if (CHANNEL_ORDER_PER_CHANNEL.equals(channel.getChannelOrder())) {
                    // each pixel for each channel

                    for (String modeTemplateChannelName : channel.getTemplateChannels()) {
                        for (CachedFixturePixel availablePixelKey : availablePixelKeys) {
                            if (availablePixelKey.getKey().equals(pixelKey)) {
                                for (Map.Entry<String, FixtureChannel> entry : profile.getTemplateChannels().getTemplateChannels().entrySet()) {
                                    // don't check the fine channels. only add the coarse channel.
                                    if (modeTemplateChannelName.equals(entry.getKey())) {
                                        this.addCachedChannel(channels, entry.getValue(), entry.getKey(), availablePixelKey.getKey(), profile);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return channels;
    }

    // get all used pixels for all channels in the currently used fixture mode
    private List<CachedFixturePixel> fixtureGetUniquePixels(Fixture fixture) {
        return fixtureGetUniquePixels(this.getProfileByUuid(fixture.getProfileUuid()), fixture);
    }

    private List<CachedFixturePixel> fixtureGetUniquePixels(FixtureProfile profile, Fixture fixture) {
        List<CachedFixturePixel> pixelKeys = new ArrayList<>();

        FixtureMode mode = this.getModeByFixture(profile, fixture);

        // add the unique pixel keys
        for (FixtureModeChannel channel : mode.getChannels()) {
            for (CachedFixturePixel pixel : getPixelsInOrder(profile, channel.getRepeatFor())) {
                if (pixelKeys.stream()
                        .noneMatch(p -> p.getKey().equals(pixel.getKey()))) {

                    pixelKeys.add(pixel);
                }
            }
        }

        return pixelKeys;
    }

    // does the fixture have a general channel without reference to specific pixel keys in the current mode?
    private boolean fixtureHasGeneralChannel(Fixture fixture) {
        return fixtureHasGeneralChannel(this.getProfileByUuid(fixture.getProfileUuid()), fixture);
    }

    private boolean fixtureHasGeneralChannel(FixtureProfile profile, Fixture fixture) {
        FixtureMode mode = this.getModeByFixture(profile, fixture);

        for (FixtureModeChannel channel : mode.getChannels()) {
            if (this.getPixelsInOrder(profile, channel.getRepeatFor()).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void updateCachedFixtures() {
        // calculate some frequently used values as a cache to save cpu time
        // afterwards
        cachedFixtures = new ArrayList<>();
        presetChases = new ConcurrentHashMap<>();
        stagePixelPlaces = new ConcurrentHashMap<>();
        stageFanPlaces = new ConcurrentHashMap<>();
        outputChannels = new ConcurrentHashMap<>();

        logger.debug("Calculate cached fixtures...");

        for (Fixture fixture : project.getFixtures()) {
            List<CachedFixture> fixtureParts = new ArrayList<>();

            if (fixtureHasGeneralChannel(fixture)) {
                CachedFixture cachedFixture = new CachedFixture();
                cachedFixture.setFixture(fixture);
                cachedFixture.setProfile(getProfileByUuid(fixture.getProfileUuid()));
                cachedFixture.setMode(getModeByFixture(cachedFixture.getProfile(), fixture));
                cachedFixture.setChannels(getCachedChannels(cachedFixture.getProfile(), cachedFixture.getMode(), null));
                fixtureParts.add(cachedFixture);
            }

            List<CachedFixturePixel> pixels = fixtureGetUniquePixels(fixture);

            for (CachedFixturePixel pixel : pixels) {
                CachedFixture cachedFixture = new CachedFixture();
                cachedFixture.setFixture(fixture);
                cachedFixture.setPixel(pixel);
                cachedFixture.setProfile(getProfileByUuid(fixture.getProfileUuid()));
                cachedFixture.setMode(getModeByFixture(cachedFixture.getProfile(), fixture));
                cachedFixture.setChannels(getCachedChannels(cachedFixture.getProfile(), cachedFixture.getMode(), pixel.getKey()));
                fixtureParts.add(cachedFixture);
            }

            addVirtualDimmers(fixtureParts);
            openColorDimmers(fixtureParts);
            cachedFixtures.addAll(fixtureParts);
        }

        sameLampIndices = getSameLampIndices(cachedFixtures);
        logger.debug("Calculated " + cachedFixtures.size() + " fixtures");
    }

    private static boolean hasCapabilityType(CachedFixture cachedFixture, FixtureCapability.FixtureCapabilityType type) {
        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability capability : channel.getCapabilities()) {
                if (capability.getCapability().getType() == type) {
                    return true;
                }
            }
        }

        return false;
    }

    // A fixture lighting in colors without a dimmer (e.g. the ADJ Dotz Par in its 3-channel mode) gets a virtual one,
    // like in the designer. It is dimmed, faded, chased and dimmed by the master dimmer like any dimmer and scales the
    // colors when they are sent (see setUniverseValues). It starts at full, so a fixture which is only given a color
    // lights in it. A fixture with a dimmer in any of its parts (e.g. a pixel bar with a master dimmer) doesn't get
    // one: that dimmer dims it already.
    private void addVirtualDimmers(List<CachedFixture> fixtureParts) {
        for (CachedFixture cachedFixture : fixtureParts) {
            if (hasCapabilityType(cachedFixture, FixtureCapability.FixtureCapabilityType.Intensity)) {
                return;
            }
        }

        for (CachedFixture cachedFixture : fixtureParts) {
            if (!hasCapabilityType(cachedFixture, FixtureCapability.FixtureCapabilityType.ColorIntensity)) {
                continue;
            }

            FixtureCapability capability = new FixtureCapability();
            capability.setType(FixtureCapability.FixtureCapabilityType.Intensity);

            FixtureChannel channel = new FixtureChannel();
            channel.setCapability(capability);
            // at full (a percentage would come out a little below it)
            channel.setDefaultValue("255");

            List<CachedFixtureChannel> channels = new ArrayList<>(cachedFixture.getChannels());
            addCachedChannel(channels, channel, VIRTUAL_DIMMER_CHANNEL_NAME, null, cachedFixture.getProfile());
            cachedFixture.setChannels(channels);
        }
    }

    // The dimmers of a fixture which lights in colors only start at full, where its profile does not say where they
    // start (see Project.colorDimmersAtFull): at the brightest the channel lights steadily, so a dimmer sharing its
    // channel with a strobe does not strobe. A color is all it takes to light the fixture then, it stays dark while its
    // colors are off. A fixture with a color wheel (white through its open slot), one mixing its colors with filters
    // (white without them) and one without colors (e.g. a halogen par) keeps its dimmers off. Like in the designer.
    private void openColorDimmers(List<CachedFixture> fixtureParts) {
        if (!project.isColorDimmersAtFull()) {
            return;
        }

        List<CachedFixtureChannel> channels = new ArrayList<>();
        boolean lightsInColors = false;

        for (CachedFixture cachedFixture : fixtureParts) {
            channels.addAll(cachedFixture.getChannels());
        }

        for (CachedFixtureChannel channel : channels) {
            if (channel.getColorWheel() != null) {
                return;
            }

            if (channel.getChannel() != null && hasCapability(channel, FixtureCapability.FixtureCapabilityType.ColorIntensity)) {
                lightsInColors = true;
            }
        }

        if (!lightsInColors || SubtractiveColors.mixesSubtractively(channels)) {
            return;
        }

        for (CachedFixtureChannel channel : channels) {
            if (channel.getChannel() == null || channel.getDefaultValue() != null) {
                continue;
            }

            List<CachedFixtureCapability> dimmers = new ArrayList<>();

            for (CachedFixtureCapability capability : channel.getCapabilities()) {
                if (capability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.Intensity) {
                    dimmers.add(capability);
                }
            }

            Double value = getBrightnessValue(channel, dimmers, 1);

            if (value != null) {
                channel.setDefaultValue(value);
            }
        }
    }

    private static boolean hasCapability(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        for (CachedFixtureCapability capability : channel.getCapabilities()) {
            if (capability.getCapability().getType() == type) {
                return true;
            }
        }

        return false;
    }

    // Unregister all universe states of a previous load/preview and forget them. They must not be
    // kept around, because setUniverseValues writes to the first state matching a mapping uuid. A
    // stale state would swallow all values, while the currently registered one stays empty.
    private void removeLightingUniverses() {
        for (LightingUniverseState lightingUniverse : lightingUniverses) {
            lightingService.removeLightingUniverse(lightingUniverse);
        }

        lightingUniverses = new ArrayList<>();
    }

    // Until version 3, the global lists of the project defined the layer order of the
    // presets and the order the fixtures are chased in. Both are stored per scene/preset
    // now -> sort them the way the global lists ordered them, so an older project still
    // looks exactly the same. Package visible for the conformance cases of the designer.
    void migrateProject(Project project) {
        if (project == null) {
            return;
        }

        if (project.getVersion() > SUPPORTED_PROJECT_VERSION) {
            logger.warn("Designer project '" + project.getName() + "' was written with a newer designer (project version " + project.getVersion() + ", this Rocket Show knows version " + SUPPORTED_PROJECT_VERSION + "). Parts of the show may be missing. Please update Rocket Show.");
        }

        warnAboutUnknownEffects(project);

        migrateProjectToVersion2(project);
        migrateProjectToVersion3(project);
        migrateProjectToVersion7(project);
        migrateProjectToVersion8(project);
        migrateProjectToVersion9(project);
        migrateProjectToVersion10(project);
        migrateProjectToVersion11(project);
    }

    // An effect of a type a newer designer added is left out (see EffectUnknown), the rest of the show still plays.
    // Reported once for each of these types.
    private void warnAboutUnknownEffects(Project project) {
        if (project.getPresets() == null) {
            return;
        }

        Set<String> unknownTypes = new LinkedHashSet<>();

        for (Preset preset : project.getPresets()) {
            if (preset.getEffects() == null) {
                continue;
            }

            for (Effect effect : preset.getEffects()) {
                if (effect instanceof EffectUnknown unknown) {
                    unknownTypes.add(unknown.getType());
                }
            }
        }

        for (String type : unknownTypes) {
            logger.warn("Designer project '" + project.getName() + "' uses effects of the type '" + type + "', which this Rocket Show does not know. They are left out. Please update Rocket Show.");
        }
    }

    // The fixtures of a preset (and of the project) are listed with their pixels now: a fixture and each of its pixels
    // can be chosen on its own. Before, a preset named its fixtures only -> list each of them with all of its pixels, in
    // the order of the fixtures, like the designer migrates them. A fixture without its profile or mode is left out.
    private void migrateProjectToVersion2(Project project) {
        if (project.getVersion() >= 2) {
            return;
        }

        if (project.getPresetFixtures() == null) {
            project.setPresetFixtures(new ArrayList<>());
        }

        if (project.getFixtures() != null) {
            for (Fixture fixture : project.getFixtures()) {
                project.getPresetFixtures().addAll(getPresetFixturesOfVersion1(project, fixture.getUuid()));
            }
        }

        if (project.getPresets() != null) {
            for (Preset preset : project.getPresets()) {
                if (preset.getFixtureUuids() != null) {
                    for (String fixtureUuid : preset.getFixtureUuids()) {
                        preset.getFixtures().addAll(getPresetFixturesOfVersion1(project, fixtureUuid));
                    }
                }

                preset.setFixtureUuids(null);
            }
        }

        project.setVersion(2);

        logger.info("Migrated designer project '" + project.getName() + "' to version 2");
    }

    // the fixture itself (if it has a channel of its own) and its pixels, as a project of version 1 meant them
    private List<PresetFixture> getPresetFixturesOfVersion1(Project project, String fixtureUuid) {
        List<PresetFixture> presetFixtures = new ArrayList<>();
        Fixture fixture = getFixtureByUuid(project, fixtureUuid);
        FixtureProfile profile = fixture == null ? null : getProfileByUuid(project, fixture.getProfileUuid());

        if (profile == null || getModeByFixture(profile, fixture) == null) {
            return presetFixtures;
        }

        if (fixtureHasGeneralChannel(profile, fixture)) {
            PresetFixture presetFixture = new PresetFixture();
            presetFixture.setFixtureUuid(fixtureUuid);
            presetFixtures.add(presetFixture);
        }

        for (CachedFixturePixel pixel : fixtureGetUniquePixels(profile, fixture)) {
            PresetFixture presetFixture = new PresetFixture();
            presetFixture.setFixtureUuid(fixtureUuid);
            presetFixture.setPixelKey(pixel.getKey());
            presetFixtures.add(presetFixture);
        }

        return presetFixtures;
    }

    private void migrateProjectToVersion3(Project project) {
        if (project.getVersion() >= 3) {
            return;
        }

        if (project.getScenes() != null && project.getPresets() != null) {
            for (Scene scene : project.getScenes()) {
                if (scene.getPresetUuids() == null) {
                    continue;
                }

                List<String> presetUuids = Arrays.asList(scene.getPresetUuids());
                List<String> sortedPresetUuids = new ArrayList<>();

                for (Preset preset : project.getPresets()) {
                    if (presetUuids.contains(preset.getUuid())) {
                        sortedPresetUuids.add(preset.getUuid());
                    }
                }

                scene.setPresetUuids(sortedPresetUuids.toArray(new String[0]));
            }
        }

        if (project.getPresets() != null && project.getPresetFixtures() != null) {
            for (Preset preset : project.getPresets()) {
                preset.getFixtures().sort(Comparator.comparingInt(presetFixture -> getGlobalFixtureIndex(project, presetFixture)));
                preset.setUseGlobalFixtureOrder(true);
            }
        }

        project.setVersion(3);

        logger.info("Migrated designer project '" + project.getName() + "' to version 3");
    }

    private void migrateProjectToVersion7(Project project) {
        if (project.getVersion() >= 7 || project.getPresets() == null) {
            return;
        }

        // a preset runs through steps now: the single look it had so far becomes its
        // first one, which is the whole preset for as long as it has no further step
        for (Preset preset : project.getPresets()) {
            PresetStep step = new PresetStep();

            if (preset.getFixtureChannelValues() != null) {
                step.setFixtureChannelValues(new ArrayList<>(preset.getFixtureChannelValues()));
            }
            if (preset.getFixtureCapabilityValues() != null) {
                step.setFixtureCapabilityValues(new ArrayList<>(preset.getFixtureCapabilityValues()));
            }

            List<PresetStep> steps = new ArrayList<>();
            steps.add(step);
            preset.setSteps(steps);
        }

        project.setVersion(7);

        logger.info("Migrated designer project '" + project.getName() + "' to version 7");
    }

    private void migrateProjectToVersion8(Project project) {
        if (project.getVersion() >= 8) {
            return;
        }

        // every curve swings over its whole amplitude now, like the square did already. the others swung over half
        // of it -> halve their amplitude, so an older project still looks exactly the same.
        if (project.getPresets() != null) {
            for (Preset preset : project.getPresets()) {
                if (preset.getEffects() == null) {
                    continue;
                }

                for (Effect effect : preset.getEffects()) {
                    // (a movement of the pan and the tilt runs on the timing of a curve, but swings over no amplitude)
                    if (effect instanceof EffectCurve curve && !(effect instanceof EffectPanTilt) && !"square".equals(curve.getCurveType())) {
                        curve.setAmplitude(curve.getAmplitude() / 2);
                    }
                }
            }
        }

        project.setVersion(8);

        logger.info("Migrated designer project '" + project.getName() + "' to version 8");
    }

    private static final Pattern LEGACY_PIXEL_KEY = Pattern.compile("Pixel (\\d+)-(\\d+)-(\\d+)");

    // The pixel keys of the passed text renamed like the fixture library names them (e.g. "Red Pixel 3-1-1" becomes
    // "Red 3"). The numbers are read like the designer reads them: one too long for an int is beside the matrix as
    // well.
    private static String renameLegacyPixelKeys(String text, List<Integer> pixelCount, boolean whole) {
        Matcher matcher = LEGACY_PIXEL_KEY.matcher(text);
        StringBuilder result = new StringBuilder();

        while (whole ? matcher.matches() : matcher.find()) {
            double x = Double.parseDouble(matcher.group(1));
            double y = Double.parseDouble(matcher.group(2));
            double z = Double.parseDouble(matcher.group(3));
            boolean inMatrix = x >= 1 && x <= pixelCount.get(0) && y >= 1 && y <= pixelCount.get(1) && z >= 1 && z <= pixelCount.get(2);

            matcher.appendReplacement(result, Matcher.quoteReplacement(inMatrix ? getDefaultPixelKey(pixelCount, (int) x, (int) y, (int) z) : matcher.group()));

            if (whole) {
                break;
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private void migrateProjectToVersion9(Project project) {
        if (project.getVersion() >= 9) {
            return;
        }

        // the pixels of a matrix given by its pixel count are named like in the fixture library now (e.g. "3" or
        // "(2, 3)" instead of "Pixel 3-1-1") -> rename them in the presets and effects, so they still light the same
        // pixels
        Map<String, List<Integer>> pixelCountByProfile = new HashMap<>();
        Map<String, List<Integer>> pixelCountByFixture = new HashMap<>();

        if (project.getFixtureProfiles() != null) {
            for (FixtureProfile profile : project.getFixtureProfiles()) {
                FixtureMatrix matrix = profile.getMatrix();

                if (matrix != null && matrix.getPixelKeys() == null && matrix.getPixelCount() != null && matrix.getPixelCount().size() == 3) {
                    pixelCountByProfile.put(profile.getUuid(), matrix.getPixelCount());
                }
            }
        }

        if (project.getFixtures() != null) {
            for (Fixture fixture : project.getFixtures()) {
                if (pixelCountByProfile.containsKey(fixture.getProfileUuid())) {
                    pixelCountByFixture.put(fixture.getUuid(), pixelCountByProfile.get(fixture.getProfileUuid()));
                }
            }
        }

        List<List<PresetFixture>> presetFixtureLists = new ArrayList<>();
        if (project.getPresetFixtures() != null) {
            presetFixtureLists.add(project.getPresetFixtures());
        }
        if (project.getPresets() != null) {
            project.getPresets().forEach(preset -> presetFixtureLists.add(preset.getFixtures()));
        }

        for (List<PresetFixture> presetFixtures : presetFixtureLists) {
            for (PresetFixture presetFixture : presetFixtures) {
                List<Integer> pixelCount = pixelCountByFixture.get(presetFixture.getFixtureUuid());

                if (presetFixture.getPixelKey() != null && pixelCount != null) {
                    presetFixture.setPixelKey(renameLegacyPixelKeys(presetFixture.getPixelKey(), pixelCount, true));
                }
            }
        }

        if (project.getPresets() != null) {
            for (Preset preset : project.getPresets()) {
                for (PresetStep step : preset.getSteps()) {
                    for (FixtureChannelValue channelValue : step.getFixtureChannelValues()) {
                        List<Integer> pixelCount = pixelCountByProfile.get(channelValue.getProfileUuid());

                        if (channelValue.getChannelName() != null && pixelCount != null) {
                            channelValue.setChannelName(renameLegacyPixelKeys(channelValue.getChannelName(), pixelCount, false));
                        }
                    }
                }

                if (preset.getEffects() == null) {
                    continue;
                }

                for (Effect effect : preset.getEffects()) {
                    if (effect instanceof EffectCurve curve && curve.getChannels() != null) {
                        for (EffectCurveProfileChannels profileChannels : curve.getChannels()) {
                            List<Integer> pixelCount = pixelCountByProfile.get(profileChannels.getProfileUuid());

                            if (pixelCount != null && profileChannels.getChannels() != null) {
                                profileChannels.setChannels(profileChannels.getChannels().stream()
                                        .map(channel -> renameLegacyPixelKeys(channel, pixelCount, false))
                                        .collect(Collectors.toList()));
                            }
                        }
                    }
                }
            }
        }

        project.setVersion(9);

        logger.info("Migrated designer project '" + project.getName() + "' to version 9");
    }

    // The timeline of a composition has tracks since version 10. Before, all scenes were played on one lane, layered in
    // the order of the scenes: the first one on top. One track per scene, in that order, plays such a show the same way.
    private void migrateProjectToVersion10(Project project) {
        if (project.getVersion() >= 10) {
            return;
        }

        if (project.getCompositions() != null && project.getScenes() != null) {
            for (Composition composition : project.getCompositions()) {
                assignTracks(composition, project.getScenes());
            }
        }

        project.setVersion(10);

        logger.info("Migrated designer project '" + project.getName() + "' to version 10");
    }

    // Version 11 brought the pixel effects (EffectPixel), which an older designer would drop when it saves the project.
    // Nothing changes.
    // Version 11 brought the pixel effects and the dimmers of the fixtures lighting in colors at full (see
    // Project.colorDimmersAtFull). An older project keeps them off, so it plays as it did, like in the designer.
    private void migrateProjectToVersion11(Project project) {
        if (project.getVersion() >= 11) {
            return;
        }

        project.setColorDimmersAtFull(false);
        project.setVersion(11);
    }

    // Put the clips of a composition which are on none of its tracks onto tracks of their own, one per scene, in the
    // order of the scenes. The clips of a scene which overlap each other get another track right below the first one
    // of the scene. A composition without clips keeps having no track. The same as assignTracks of the designer.
    static void assignTracks(Composition composition, List<Scene> scenes) {
        if (composition.getScenePlaybackRegions() == null) {
            return;
        }

        Set<String> trackUuids = composition.getTracks().stream().map(Track::getUuid).collect(Collectors.toSet());
        List<ScenePlaybackRegion> unassigned = Arrays.stream(composition.getScenePlaybackRegions())
                .filter(region -> !trackUuids.contains(region.getTrackUuid()))
                .collect(Collectors.toList());

        for (Scene scene : scenes) {
            List<ScenePlaybackRegion> regions = unassigned.stream()
                    .filter(region -> scene.getUuid().equals(region.getSceneUuid()))
                    .sorted(Comparator.comparingDouble(ScenePlaybackRegion::getStartMillis))
                    .collect(Collectors.toList());
            List<Track> sceneTracks = new ArrayList<>();

            for (ScenePlaybackRegion region : regions) {
                Track track = sceneTracks.stream()
                        .filter(candidate -> isFreeOnTrack(composition, candidate.getUuid(), region))
                        .findFirst()
                        .orElse(null);

                if (track == null) {
                    track = new Track();
                    track.setUuid(UUID.randomUUID().toString());

                    // the tracks of a scene without a name are named like new ones (e.g. "Track 3")
                    if (scene.getName() == null || scene.getName().isEmpty()) {
                        track.setName(getNewTrackName(composition));
                    } else {
                        track.setName(sceneTracks.isEmpty() ? scene.getName() : scene.getName() + " " + (sceneTracks.size() + 1));
                    }

                    sceneTracks.add(track);
                    composition.getTracks().add(track);
                }

                region.setTrackUuid(track.getUuid());
            }
        }
    }

    // A name for another track of the composition, which none of its tracks carries yet, like getNewTrackName of the
    // designer
    private static String getNewTrackName(Composition composition) {
        int number = composition.getTracks().size() + 1;

        while (true) {
            String name = "Track " + number;

            if (composition.getTracks().stream().noneMatch(track -> name.equals(track.getName()))) {
                return name;
            }

            number++;
        }
    }

    // is the time of the passed clip still free on the track? A clip ends where the next one may start.
    private static boolean isFreeOnTrack(Composition composition, String trackUuid, ScenePlaybackRegion region) {
        for (ScenePlaybackRegion other : composition.getScenePlaybackRegions()) {
            if (other != region && trackUuid.equals(other.getTrackUuid())
                    && other.getEndMillis() > region.getStartMillis() && other.getStartMillis() < region.getEndMillis()) {
                return false;
            }
        }

        return true;
    }

    private int getGlobalFixtureIndex(Project project, PresetFixture presetFixture) {
        for (int i = 0; i < project.getPresetFixtures().size(); i++) {
            PresetFixture projectFixture = project.getPresetFixtures().get(i);

            if (fixtureUuidAndPixelKeyEquals(projectFixture.getFixtureUuid(), presetFixture.getFixtureUuid(), projectFixture.getPixelKey(), presetFixture.getPixelKey())) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public synchronized void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline) {
        // The universes of the project played so far are replaced -> nothing may be sending them anymore, e.g. the
        // scenes picked by hand, which a composition takes the lights over from
        stopTimer();
        playingManualScenes = false;

        migrateProject(project);

        this.compositionPlayer = compositionPlayer;
        this.project = project;
        this.pipeline = pipeline;

        // a fixture is only identified while the designer previews the patch it is in (see setIdentifyFixtureUuids)
        this.identifyFixtureUuids = new ArrayList<>();
        this.resetFixtureUuids = new ArrayList<>();
        this.lampOnFixtureUuids = new ArrayList<>();
        this.dmxOverrides = new ArrayList<>();

        // a preview names the composition it plays, if any (see setPreviewComposition)
        this.composition = compositionPlayer == null ? null : getCompositionByName(project, compositionPlayer.getComposition().getName());

        // the pipeline of a composition measures the buses an effect of the project follows
        audioAnalysisService.setRequiredBuses(followedBuses(project, settingsService.getSettings().getAudioBusList()));

        // Create the caches
        updateCachedFixtures();

        // A previous load might still hold universe states (e.g. resuming a paused composition
        // calls load again without closing first)
        removeLightingUniverses();

        for (LightingUniverse lightingUniverse : settingsService.getSettings().getLightingUniverseList()) {
            LightingUniverseState newUniverse = new LightingUniverseState();
            newUniverse.setMappingUuid(lightingUniverse.getUuid());
            lightingUniverses.add(newUniverse);
            lightingService.addLightingUniverse(newUniverse);
        }

        lightingService.setExternalSync(true);
    }

    @Override
    public void play() {
        if (project == null) {
            return;
        }

        startTimer();
    }

    @Override
    public void pause() {
        // The scenes picked by hand keep playing, e.g. while a composition without a project of its own is paused
        if (project == null || playingManualScenes) {
            return;
        }

        stopTimer();
    }

    @Override
    public synchronized void close() {
        // the composition is over: so are the holds it went through
        forgetHolds(0);

        // The scenes picked by hand on the Play screen aren't the project of a composition: a composition loading or
        // stopping leaves them playing
        if (project == null || playingManualScenes) {
            return;
        }

        stopTimer();

        removeLightingUniverses();

        project = null;
        pipeline = null;

        // Nothing plays a project of its own anymore -> the scenes picked by hand come back, unless they were let go
        // meanwhile
        if (hasManualScenes()) {
            if (releasingManualScenes) {
                stopManualScenes();
            } else {
                startManualScenes();
            }
        }
    }

    @Override
    public synchronized void playManualScenes(Project project, List<String> sceneUuids, List<String> flashedSceneUuids, double masterDimmer) {
        manualSceneUuids = new ArrayList<>(sceneUuids);
        this.flashedSceneUuids = flashedSceneUuids == null ? new ArrayList<>() : new ArrayList<>(flashedSceneUuids);
        manualScenesMasterDimmer = masterDimmer;
        releasingManualScenes = false;

        if (playingManualScenes && project == manualScenesProject) {
            // The project plays already: the engine picks the scenes and the master dimmer up with its next universe,
            // without starting the effects over (e.g. while the master dimmer is being dragged). A scene picked starts
            // there, one let go fades out.
            selectedSceneUuids = manualSceneUuids;
            updateLiveScenes();
            return;
        }

        manualScenesProject = project;

        if (this.project != null && compositionPlayer != null && !compositionPlayer.isDefaultComposition()) {
            // A composition plays its own project: the scenes wait for it to close. The default composition is only
            // played in between, the scenes picked by hand go before its lights.
            return;
        }

        startManualScenes();
    }

    // Play the scenes picked by hand, the way the designer previews the scenes selected in it
    private void startManualScenes() {
        load(null, manualScenesProject, null);

        playPreview = false;
        previewPreset = false;
        selectedPresetUuid = null;
        selectedStepUuid = null;
        stepPreviewRunning = false;
        selectedSceneUuids = manualSceneUuids;
        playingManualScenes = true;

        // the scenes start anew, on the beats of the tempo playing
        liveScenes.reset();
        syncBeatClock();
        updateLiveScenes();

        startPositionMillis = 0;
        startTimer();
    }

    // hand the scenes picked and flashed to the live scenes, which picks the new ones and lets go of the others
    private void updateLiveScenes() {
        liveScenes.setScenes(getScenesByUuids(manualSceneUuids), getScenesByUuids(flashedSceneUuids), System.currentTimeMillis());
    }

    private List<Scene> getScenesByUuids(List<String> sceneUuids) {
        List<Scene> scenes = new ArrayList<>();

        for (String sceneUuid : sceneUuids) {
            Scene scene = getSceneByUuid(sceneUuid);

            if (scene != null) {
                scenes.add(scene);
            }
        }

        return scenes;
    }

    @Override
    public synchronized void releaseManualScenes() {
        if (!playingManualScenes) {
            stopManualScenes();
            return;
        }

        // the scenes fade out, then the timer stops them (see startTimer)
        manualSceneUuids = new ArrayList<>();
        flashedSceneUuids = new ArrayList<>();
        selectedSceneUuids = manualSceneUuids;
        updateLiveScenes();
        releasingManualScenes = true;
    }

    // the scenes let go have faded out: stop them, unless scenes were picked again meanwhile
    private synchronized void stopReleasedManualScenes() {
        if (playingManualScenes && manualSceneUuids.isEmpty() && flashedSceneUuids.isEmpty() && liveScenes.isIdle(System.currentTimeMillis())) {
            stopManualScenes();
        }
    }

    @Override
    public void setLiveSceneDimmers(Map<String, Double> dimmers) {
        liveScenes.setDimmers(dimmers);
    }

    @Override
    public void setLiveSpeed(double speed) {
        liveScenes.setSpeed(speed, System.currentTimeMillis());
    }

    @Override
    public synchronized void stopManualScenes() {
        manualScenesProject = null;
        manualSceneUuids = new ArrayList<>();
        flashedSceneUuids = new ArrayList<>();
        manualScenesMasterDimmer = 1;
        releasingManualScenes = false;
        liveScenes.reset();

        if (!playingManualScenes) {
            return;
        }

        playingManualScenes = false;
        stopTimer();
        removeLightingUniverses();
        project = null;

        // Nothing sends the universes of the scenes anymore -> turn the lights off, instead of leaving the values sent
        // last on the wire
        lightingService.setExternalSync(false);
        lightingService.reset();
    }

    @Override
    public boolean hasManualScenes() {
        return manualScenesProject != null;
    }

    @Override
    public boolean isPlayingManualScenes() {
        return playingManualScenes;
    }

    @Override
    public void startPreview(long positionMillis) {
        if (project == null) {
            logger.debug("No project available to preview");
            return;
        }

        this.startPositionMillis = positionMillis;
        startTimer();
    }

    @Override
    public void stopPreview() {
        // The scenes picked by hand on the Play screen aren't a preview of the designer: they keep playing, e.g. when
        // the live preview is turned off in the settings
        if (project == null || playingManualScenes) {
            return;
        }

        stopTimer();

        removeLightingUniverses();
    }

    @Override
    public void setPreviewComposition(String compositionName) {
        if (compositionName == null || compositionName.isEmpty()) {
            this.playPreview = false;
        } else {
            composition = getCompositionByName(project, compositionName);
            playPreview = true;
        }
    }

    @Override
    public void setPreviewPreset(boolean previewPreset) {
        this.previewPreset = previewPreset;
    }

    @Override
    public void setSelectedPresetUuid(String selectedPresetUuid) {
        this.selectedPresetUuid = selectedPresetUuid;
    }

    @Override
    public void setSelectedStepUuid(String selectedStepUuid) {
        this.selectedStepUuid = selectedStepUuid;
    }

    @Override
    public void setStepPreviewRunning(boolean stepPreviewRunning, double stepPreviewStartMillis) {
        this.stepPreviewRunning = stepPreviewRunning;
        this.stepPreviewStartMillis = stepPreviewStartMillis;
    }

    // the holds the timeline of the composition went through (see CompositionHold)
    @Override
    public void setHolds(List<CompositionHold> holds) {
        this.holds = holds == null ? new ArrayList<>() : new ArrayList<>(holds);
    }

    @Override
    public void setSelectedSceneUuids(List<String> selectedSceneUuids) {
        this.selectedSceneUuids = selectedSceneUuids;
    }

    @Override
    public void setIdentifyFixtureUuids(List<String> identifyFixtureUuids) {
        this.identifyFixtureUuids = identifyFixtureUuids == null ? new ArrayList<>() : new ArrayList<>(identifyFixtureUuids);
    }

    @Override
    public void setMaintenanceFixtureUuids(List<String> resetFixtureUuids, List<String> lampOnFixtureUuids) {
        this.resetFixtureUuids = resetFixtureUuids == null ? new ArrayList<>() : new ArrayList<>(resetFixtureUuids);
        this.lampOnFixtureUuids = lampOnFixtureUuids == null ? new ArrayList<>() : new ArrayList<>(lampOnFixtureUuids);
    }

    @Override
    public void setDmxOverrides(List<DmxOverride> dmxOverrides) {
        this.dmxOverrides = dmxOverrides == null ? new ArrayList<>() : new ArrayList<>(dmxOverrides);
    }

    @Override
    public Project getCurrentProject() {
        return project;
    }
}
