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
import org.springframework.stereotype.Service;
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
    static final int SUPPORTED_PROJECT_VERSION = 10;

    // Live preview
    private boolean playPreview = false; // Is the preview playing a composition?
    private boolean previewPreset = false;
    private String selectedPresetUuid;
    private String selectedStepUuid;
    private boolean stepPreviewRunning = false;
    private long stepPreviewStartMillis = 0;
    private List<String> selectedSceneUuids = new ArrayList<>();

    // The scenes picked by hand on the Play screen (see ScenePlayerService), the project they are from and the master
    // dimmer of the Play screen. They play whenever no composition plays a project of its own: right away, or once the
    // composition closes its project.
    // (read by the timer sending the universes and by the players of the compositions)
    private Project manualScenesProject;
    private List<String> manualSceneUuids = new ArrayList<>();
    private volatile double manualScenesMasterDimmer = 1;
    private volatile boolean playingManualScenes = false;

    private List<LightingUniverseState> lightingUniverses = new ArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> universeSenderHandle;

    // Held while the timer sends a universe, so stopping the timer waits for a send running at the moment
    private final Object universeSendLock = new Object();

    private List<CachedFixture> cachedFixtures;

    private record PresetFixtureKey(String fixtureUuid, String pixelKey) {
    }

    // the place of each fixture of a preset in its chase (see calculatePresetChase) and the number of its steps
    private record PresetChase(Map<PresetFixtureKey, Integer> fixtureIndices, int fixtureCount) {
    }

    // the chases of the presets of the loaded project, by preset (presets don't override equals)
    private Map<Preset, PresetChase> presetChases = new ConcurrentHashMap<>();

    private long lastPlayTimeMillis;

    public DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService,
            FixturePixelGroupResolveService fixturePixelGroupResolveService
    ) {
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
                        ObjectMapper mapper = new ObjectMapper();
                        Project project = mapper.readValue(file, Project.class);

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

        return getClipsEndMillis(project, getCompositionByName(project, compositionName));
    }

    // The end of the last clip of the composition, or of a fade running past the end of a clip (of its scene, or of one
    // of the presets played in it). Worked out like the designer does it.
    static long getClipsEndMillis(Project project, Composition composition) {
        long endMillis = 0;

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
    static long getSceneStartMillis(Scene scene, ScenePlaybackRegion region) {
        return scene != null && scene.isFadeInPre() ? region.getStartMillis() - scene.getFadeInMillis() : region.getStartMillis();
    }

    static long getSceneEndMillis(Scene scene, ScenePlaybackRegion region) {
        return scene != null && scene.isFadeOutPost() ? region.getEndMillis() + scene.getFadeOutMillis() : region.getEndMillis();
    }

    // When a preset played in the scene of a clip plays, its own fades outside its times included. A preset without
    // times of its own plays as long as the scene does, also while the scene fades in or out outside the clip.
    static long getPresetPlayStartMillis(Preset preset, ScenePlaybackRegion region, Scene scene) {
        long startMillis = preset.getStartMillis() == null ? getSceneStartMillis(scene, region) : region.getStartMillis() + preset.getStartMillis();

        return startMillis - (preset.isFadeInPre() ? preset.getFadeInMillis() : 0);
    }

    static long getPresetPlayEndMillis(Preset preset, ScenePlaybackRegion region, Scene scene) {
        long endMillis = preset.getEndMillis() == null ? getSceneEndMillis(scene, region) : region.getStartMillis() + preset.getEndMillis();

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
        ObjectMapper mapper = new ObjectMapper();
        Project projectObject = mapper.readValue(project, Project.class);
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
                .sorted(Comparator.comparingLong(ScenePlaybackRegion::getStartMillis))
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

    private List<PresetRegionScene> getPresetsInTime(long timeMillis) {
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

    private List<PresetRegionScene> getPresets(long timeMillis) {
        // Get relevant presets in correct order to process with their corresponding scene, if available
        List<PresetRegionScene> presets = new ArrayList<>();

        if (playPreview || (compositionPlayer != null && CompositionPlayer.PlayState.PLAYING.equals(compositionPlayer.getPlayState()))) {
            // Only use active presets in current regions
            presets = getPresetsInTime(timeMillis);
        } else {
            // previewPreset = solo. Without a selected scene there is nothing else to
            // show than the selected preset anyway.
            if (previewPreset || selectedSceneUuids == null || selectedSceneUuids.isEmpty()) {
                // Only preview the selected preset
                if (selectedPresetUuid != null) {
                    presets.add(new PresetRegionScene(getPresetByUuid(selectedPresetUuid), null, null));
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

    private boolean fixtureUuidAndPixelKeyEquals(String fixtureUuid1, String fixtureUuid2, String pixelKey1, String pixelKey2) {
        return fixtureUuid1.equals(fixtureUuid2) && ((pixelKey1 == null && pixelKey2 == null) || Objects.equals(pixelKey1, pixelKey2));
    }

    private DmxChannelAndPixelKey getDmxChannelAndPixelKey(PresetFixture presetFixture) {
        Fixture fixture = getFixtureByUuid(project, presetFixture.getFixtureUuid());

        if (fixture == null) {
            return null;
        }

        DmxChannelAndPixelKey dmxChannelAndPixelKey = new DmxChannelAndPixelKey();
        dmxChannelAndPixelKey.setDmxChannel(fixture.getDmxFirstChannel());
        dmxChannelAndPixelKey.setPixelKey(presetFixture.getPixelKey());
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
        }

        Set<DmxChannelAndPixelKey> lamps = new HashSet<>();
        for (PresetFixture presetFixture : preset.getFixtures()) {
            DmxChannelAndPixelKey firstDmxChannelAndPixelKey = getDmxChannelAndPixelKey(presetFixture);
            if (firstDmxChannelAndPixelKey != null) {
                lamps.add(firstDmxChannelAndPixelKey);
            }
        }

        return new PresetChase(fixtureIndices, lamps.size());
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
        if (project == null) {
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

    private double getMaxValueByChannel(FixtureChannel fixtureChannel) {
        return Math.pow(256, 1 + fixtureChannel.getFineChannelAliases().size()) - 1;
    }

    private Double getDefaultValueByChannel(FixtureChannel fixtureChannel) {
        if (fixtureChannel.getDefaultValue() == null) {
            return null;
        }

        if (!isNumeric(fixtureChannel.getDefaultValue()) && fixtureChannel.getDefaultValue().endsWith("%")) {
            // percentage value
            // the fixture library also has fractions, e.g. 33.3%
            double percentage = Double.parseDouble(fixtureChannel.getDefaultValue().replace("%", ""));
            double maxValue = getMaxValueByChannel(fixtureChannel);
            return maxValue / 100 * percentage;
        } else {
            // DMX value
            return Double.parseDouble(fixtureChannel.getDefaultValue());
        }
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean wheelHasSlotType(FixtureWheel wheel, FixtureWheelSlot.FixtureWheelSlotType slotType) {
        if (wheel == null) {
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

    private CachedFixtureCapability getFixtureCapability(FixtureCapability capability, String channelName, FixtureProfile profile) {
        CachedFixtureCapability cachedFixtureCapability = new CachedFixtureCapability();
        cachedFixtureCapability.setCapability(capability);
        if (capability.getSlotNumber() != null) {
            // there is a wheel connected to this capability
            if (capability.getWheel() instanceof String) {
                cachedFixtureCapability.setWheelName((String) capability.getWheel());
            } else if (capability.getWheel() instanceof String[]) {
                cachedFixtureCapability.setWheelName(((String[]) capability.getWheel())[0]);
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

        if (fixtureChannel.getCapability() != null) {
            capabilities.add(getFixtureCapability(fixtureChannel.getCapability(), channelName, profile));
        } else if (fixtureChannel.getCapabilities() != null) {
            for (FixtureCapability capability : fixtureChannel.getCapabilities()) {
                capabilities.add(getFixtureCapability(capability, channelName, profile));
            }
        }

        return capabilities;
    }

    private boolean capabilitiesMatch(FixtureCapability.FixtureCapabilityType type1, FixtureCapability.FixtureCapabilityType type2, FixtureCapability.FixtureCapabilityColor color1, FixtureCapability.FixtureCapabilityColor color2, String wheel1, String wheel2, String profileUuid1, String profileUuid2) {
        // checks, whether two provided capapabilities match
        return type1 == type2 && (color1 == null || color1 == color2) && (wheel1 == null || wheel1.equals(wheel2)) && (profileUuid1 == null || profileUuid1.equals(profileUuid2));
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

    private Color hexToRgb(String hex) {
        return new Color(Double.valueOf(Integer.valueOf(hex.substring(1, 3), 16)), Double.valueOf(Integer.valueOf(hex.substring(3, 5), 16)), Double.valueOf(Integer.valueOf(hex.substring(5, 7), 16)));
    }

    private Color mixColors(List<Color> colors) {
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
        List<String> colors = getWheelSlotColors(wheel, slotNumber);
        List<Color> colorsRgb = new ArrayList<>();

        if (colors.isEmpty()) {
            return null;
        }

        for (String color : colors) {
            colorsRgb.add(hexToRgb(color));
        }

        return mixColors(colorsRgb);
    }

    private CachedFixtureCapability getApproximatedColorWheelCapability(List<FixtureCapabilityValue> capabilityValues, CachedFixtureChannel cachedChannel) {
        // return an approximated wheel slot channel capability, if a color or a slot on a different
        // wheel has been selected
        Double colorRed = null;
        Double colorGreen = null;
        Double colorBlue = null;
        double lowestDiff = Double.MAX_VALUE;
        CachedFixtureCapability lowestDiffCapability = null;
        FixtureCapabilityValue capabilityValue;

        capabilityValue = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Red, null, null);
        if (capabilityValue != null) {
            colorRed = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Green, null, null);
        if (capabilityValue != null) {
            colorGreen = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = getCapabilityValue(capabilityValues, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Blue, null, null);
        if (capabilityValue != null) {
            colorBlue = 255 * capabilityValue.getValuePercentage();
        }

        if (colorRed == null && colorGreen == null && colorBlue == null) {
            // no color found -> search the first color wheel
            // TODO
        }

        if (colorRed != null && colorGreen != null && colorBlue != null) {
            for (CachedFixtureCapability capability : cachedChannel.getCapabilities()) {
                if (capability.getCapability().getSlotNumber() != null) {
                    Color mixedColor = getMixedWheelSlotColor(capability.getWheel(), capability.getCapability().getSlotNumber());
                    if (mixedColor != null) {
                        double diff = Math.abs(mixedColor.getRed() - colorRed) + Math.abs(mixedColor.getGreen() - colorGreen) + Math.abs(mixedColor.getBlue() - colorBlue);
                        if (diff < lowestDiff) {
                            lowestDiff = diff;
                            lowestDiffCapability = capability;
                        }
                    }
                }
            }
        }

        return lowestDiffCapability;
    }

    private CachedFixture getAlreadyCalculatedFixture(List<CachedFixture> fixtures, int fixtureIndex) {
        // Has this fixture already been calculated (same universe and dmx start address as a fixture before)
        // --> return it
        CachedFixture currentFixture = fixtures.get(fixtureIndex);

        for (int i = 0; i < fixtureIndex; i++) {
            CachedFixture calculatedFixture = fixtures.get(i);

            if (Objects.equals(calculatedFixture.getFixture().getDmxUniverseUuid(),
                    currentFixture.getFixture().getDmxUniverseUuid())
                    && calculatedFixture.getFixture().getDmxFirstChannel()
                    == currentFixture.getFixture().getDmxFirstChannel()
                    && Objects.equals(
                    calculatedFixture.getPixel() != null
                            ? calculatedFixture.getPixel().getKey()
                            : null,
                    currentFixture.getPixel() != null
                            ? currentFixture.getPixel().getKey()
                            : null)) {

                return calculatedFixture;
            }
        }

        return null;
    }

    // where the preset itself starts, which is what its steps are timed against. The
    // fades may reach outside it, but a step at 0 belongs to the preset's own start.
    private long getPresetStartMillis(PresetRegionScene preset) {
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
    private PresetStepState getPresetStepState(PresetRegionScene preset, double timeMillis) {
        if (preset.getRegion() != null) {
            return PresetSteps.getStateAtMillis(preset.getPreset(), Math.round(timeMillis - getPresetStartMillis(preset)));
        }

        if (stepPreviewRunning) {
            // the sequence was started by hand, so it runs from the point it was started at
            return PresetSteps.getStateAtMillis(preset.getPreset(), Math.round(timeMillis - stepPreviewStartMillis));
        }

        List<PresetStep> steps = preset.getPreset().getSteps();

        if (steps == null || steps.isEmpty()) {
            // an old project keeps its values on the preset itself
            return PresetSteps.getStateAtMillis(preset.getPreset(), 0);
        }

        return PresetSteps.getStepState(getEditStep(preset.getPreset()));
    }

    // worked out from the passed preset alone, so the specs can run it without a service
    static double getPresetIntensity(PresetRegionScene preset, long timeMillis) {
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
            long sceneStartMillis = getSceneStartMillis(preset.getScene(), preset.getRegion());
            long sceneEndMillis = getSceneEndMillis(preset.getScene(), preset.getRegion());

            // the curve maps how far the fade has come to how much of the intensity it has
            // already handed over, which shapes a fade out just as well as a fade in
            if (timeMillis > sceneEndMillis - preset.getScene().getFadeOutMillis() && timeMillis < sceneEndMillis) {
                // Scene fades out
                intensityPercentageScene *= TransitionCurve.apply(preset.getScene().getFadeOutCurve(), ((double) (sceneEndMillis - timeMillis)) / ((double) preset.getScene().getFadeOutMillis()));
            } else if (timeMillis < sceneStartMillis + preset.getScene().getFadeInMillis() && timeMillis > sceneStartMillis) {
                // Scene fades in
                intensityPercentageScene *= TransitionCurve.apply(preset.getScene().getFadeInCurve(), ((double) (timeMillis - sceneStartMillis)) / ((double) preset.getScene().getFadeInMillis()));
            }
        }

        if (preset.getRegion() != null && preset.getPreset() != null) {
            // Take away intensity for preset fading. Without times of its own, the preset plays as long as the scene,
            // its fades outside the clip included.
            long presetStartMillis = getPresetPlayStartMillis(preset.getPreset(), preset.getRegion(), preset.getScene());
            long presetEndMillis = getPresetPlayEndMillis(preset.getPreset(), preset.getRegion(), preset.getScene());

            if (timeMillis > presetEndMillis - preset.getPreset().getFadeOutMillis() && timeMillis < presetEndMillis) {
                // Preset fades out
                intensityPercentagePreset = TransitionCurve.apply(preset.getPreset().getFadeOutCurve(), ((double) (presetEndMillis - timeMillis)) / ((double) preset.getPreset().getFadeOutMillis()));
            } else if (timeMillis < presetStartMillis + preset.getPreset().getFadeInMillis() && timeMillis > presetStartMillis) {
                // Preset fades in
                intensityPercentagePreset = TransitionCurve.apply(preset.getPreset().getFadeInCurve(), ((double) (timeMillis - presetStartMillis)) / ((double) preset.getPreset().getFadeInMillis()));
            }
        }

        return intensityPercentageScene * intensityPercentagePreset;
    }

    private static boolean isBrightness(FixtureCapabilityValue capabilityValue) {
        return (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity || capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity)
                && capabilityValue.getValuePercentage() != null;
    }

    private static Double parseBrightnessNumber(String brightness, String unit) {
        try {
            return Double.parseDouble(brightness.substring(0, brightness.length() - unit.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
            if (capabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity && capabilityValue.getValuePercentage() != null
                    && (capabilityValue.getProfileUuid() == null || capabilityValue.getProfileUuid().equals(cachedFixture.getProfile().getUuid()))) {
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

    private void mixCapabilityValues(PresetStepState state, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage, Preset preset) {
        boolean hasColor = false;

        // how bright the fixture lights below this preset and how bright the preset lights it, to blend the colors
        // while the preset fades (see mixColorValue)
        Double brightnessBelow = getDimmerBrightness(cachedFixture, values);
        double presetBrightness = brightnessBelow == null ? 0 : getPresetDimmerBrightness(state, cachedFixture, preset, brightnessBelow);

        // mix the capability values of the state the preset is in
        for (FixtureCapabilityValue presetCapabilityValue : state.getFixtureCapabilityValues()) {
            for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                if (cachedChannel.getChannel() != null) {
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
                            } else if ((presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Pan || presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Tilt) && presetCapabilityValue.getValuePercentage() != null) {
                                // a preset which mirrors this axis points its fixtures at the mirrored position
                                double valuePercentage = preset.getMirroredValuePercentage(presetCapabilityValue.getType(), presetCapabilityValue.getValuePercentage());

                                FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                fixtureChannelValue.setChannelName(cachedChannel.getName());
                                fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                fixtureChannelValue.setValue(cachedChannel.getMaxValue() * valuePercentage);
                                this.mixChannelValue(values, fixtureChannelValue, 1);
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

                // approximate the color from a color or a different color wheel, if necessary
                if (!hasColor && cachedChannel.getColorWheel() != null) {
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
    private Map<CachedFixture, List<FixtureChannelValue>> getChannelValues(long timeMillis, List<PresetRegionScene> presets) {
        // Loop over all relevant presets and calc the property values from the presets (capabilities and effects)
        HashMap<CachedFixture, List<FixtureChannelValue>> calculatedFixtures = new HashMap<>();

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
                presetStates.put(preset, getPresetStepState(preset, timeMillis));
            }
        }

        for (int i = 0; i < cachedFixtures.size(); i++) {
            CachedFixture cachedFixture = cachedFixtures.get(i);

            // all values of the current fixture channels
            List<FixtureChannelValue> values = new ArrayList<>();

            CachedFixture alreadyCalculatedFixture = getAlreadyCalculatedFixture(cachedFixtures, i);

            if (logger.isTraceEnabled()) {
                logger.trace("Calculate fixture: " + cachedFixture.getProfile().getName());
            }

            if (alreadyCalculatedFixture == null) {
                // apply the default values
                for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                    if (cachedChannel.getChannel() != null) {
                        if (cachedChannel.getChannel().getDefaultValue() != null) {
                            FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                            fixtureChannelValue.setChannelName(cachedChannel.getName());
                            fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                            fixtureChannelValue.setValue(cachedChannel.getDefaultValue());
                            this.mixChannelValue(values, fixtureChannelValue, 1);
                        }
                    }
                }

                for (PresetRegionScene preset : presets) {
                    // search for this fixture in the preset and get it's preset-specific index (for chasing effects)
                    String pixelKey = cachedFixture.getPixel() != null ? cachedFixture.getPixel().getKey() : null;
                    Integer fixtureIndex = getFixtureIndex(preset.getPreset(), cachedFixture.getFixture().getUuid(), pixelKey);

                    if (fixtureIndex != null) {
                        // this fixture is also in the preset -> mix the required values (overwrite existing values,
                        // if set multiple times)
                        double intensityPercentage = getPresetIntensity(preset, timeMillis);
                        PresetStepState state = presetStates.get(preset);

                        if (state == null) {
                            state = getPresetStepState(preset, timeMillis - PresetSteps.getStepsPhasingMillis(preset.getPreset(), fixtureIndex, fixtureCounts.get(preset.getPreset())));
                        }

                        mixCapabilityValues(state, cachedFixture, values, intensityPercentage, preset.getPreset());
                        mixChannelValues(state, cachedFixture, values, intensityPercentage);
                        mixEffects(timeMillis, fixtureIndex, fixtureCounts.get(preset.getPreset()), preset, cachedFixture, values, intensityPercentage, state);
                    }
                }
            }

            // Store the calculated values for subsequent fixtures on the same DMX address
            calculatedFixtures.put(cachedFixture, values);
        }

        return calculatedFixtures;
    }

    // the tempo the curves synced to the beat follow. a preset can be previewed without a
    // composition being played, which leaves the curves on their default tempo.
    private Double getBeatsPerMinute() {
        return composition == null ? null : composition.getBeatsPerMinute();
    }

    // a preset that is not placed in a composition has no moment it starts at: the preview
    // clock has been running since the designer was opened, so a curve that stops after a
    // while would always be over already. repeat its run instead, the way the grid of the
    // effect shows it.
    private long getCurveTimeMillis(EffectCurve curve, long effectTimeMillis, PresetRegionScene preset, Integer fixtureCount, Double beatsPerMinute) {
        if (preset.getRegion() != null) {
            return effectTimeMillis;
        }

        Double loopMillis = curve.getRunLoopMillis(fixtureCount, beatsPerMinute);

        if (loopMillis == null) {
            return effectTimeMillis;
        }

        return (long) ((effectTimeMillis % loopMillis + loopMillis) % loopMillis);
    }

    private void mixEffects(long timeMillis, int fixtureIndex, Integer fixtureCount, PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage, PresetStepState state) {
        long effectTimeMillis = timeMillis;

        if (preset.getRegion() != null) {
            effectTimeMillis = timeMillis - preset.getRegion().getStartMillis();
        }

        for (Effect effect : preset.getPreset().getEffects()) {
            // the effect keeps running through the steps, they only open or close it
            double effectAmount = state.getEffectAmount(effect.getUuid());
            double effectIntensityPercentage = intensityPercentage * effectAmount;

            if (effect.isVisible() && effectAmount > 0) {
                // EffectCurve
                if (effect instanceof EffectCurve effectCurve) {
                    Double beatsPerMinute = this.getBeatsPerMinute();
                    long curveTimeMillis = getCurveTimeMillis(effectCurve, effectTimeMillis, preset, fixtureCount, beatsPerMinute);

                    // capabilities
                    for (FixtureCapability capability : effectCurve.getCapabilities()) {
                        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                            for (CachedFixtureCapability channelCapability : cachedChannel.getCapabilities()) {
                                if (capabilitiesMatch(capability.getType(), channelCapability.getCapability().getType(), capability.getColor(), channelCapability.getCapability().getColor(), null, null, null, null)) {
                                    Double value = effectCurve.getValueAtMillis(curveTimeMillis, fixtureIndex, fixtureCount, beatsPerMinute);

                                    // the curve does not apply anymore after it has finished running
                                    if (value != null) {
                                        // a preset which mirrors this axis mirrors the movement of its curves as well
                                        value = preset.getPreset().getMirroredValuePercentage(capability.getType(), value);

                                        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                        fixtureChannelValue.setChannelName(cachedChannel.getName());
                                        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                        fixtureChannelValue.setValue(cachedChannel.getMaxValue() * value);
                                        mixChannelValue(values, fixtureChannelValue, effectIntensityPercentage);
                                    }
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
                                        Double value = effectCurve.getValueAtMillis(curveTimeMillis, fixtureIndex, fixtureCount, beatsPerMinute);

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

                // TODO other effects (PanTilt, etc.)
            }
        }
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

    private void setUniverseValueForChannel(HashMap<Integer, Integer> universe, int channelIndex, CachedFixture cachedFixture, String templateChannelName, String pixelKey, List<FixtureChannelValue> fixtureChannelValues, Double colorDimmer) {
        String channelName = getChannelNameWithPixelKey(templateChannelName, pixelKey);

        // fine channels are not cached separately -> this returns the coarse channel for them
        CachedFixtureChannel channel = getChannelByName(cachedFixture, channelName);

        if (channel == null || channel.getChannel() == null) {
            return;
        }

        int fineIndex = getFineIndexByChannelName(channel, channelName);

        // match this mode channel with the value of its coarse channel
        for (FixtureChannelValue channelValue : fixtureChannelValues) {
            if (channel.getName().equals(channelValue.getChannelName())) {
                double value = applyMasterDimmer(channel, channelValue.getValue());

                if (colorDimmer != null) {
                    value = dim(channel, value, FixtureCapability.FixtureCapabilityType.ColorIntensity, colorDimmer);
                }

                int dmxValue = (int) Math.floor(value / Math.pow(256, channel.getChannel().getFineChannelAliases().size() - (fineIndex + 1))) % 256;
                universe.put(channelIndex, dmxValue);
                break;
            }
        }
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

        // Reset all DMX universes
        for (LightingUniverseState universe : lightingUniverses) {
            universe.reset();
        }

        // loop over each fixture with its values for each channel
        for (Map.Entry<CachedFixture, List<FixtureChannelValue>> entry : fixtures.entrySet()) {
            String fixtureUniverseUuid = entry.getKey().getFixture().getDmxUniverseUuid();
            HashMap<Integer, Integer> universe = lightingUniverses.stream()
                    .filter(u -> Objects.equals(u.getMappingUuid(), fixtureUniverseUuid))
                    .map(LightingUniverseState::getUniverse)
                    .findFirst()
                    .orElse(null);

            if (universe == null) {
                continue;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Set universe values for " + entry.getValue().size() + " channels of fixture " + entry.getKey().getFixture().getName());
            }

            Double colorDimmer = getColorDimmer(entry.getKey(), entry.getValue());

            // loop over each channel for this fixture
            int channelIndex = entry.getKey().getFixture().getDmxFirstChannel();

            for (FixtureModeChannel fixtureModeChannel : entry.getKey().getMode().getChannels()) {
                logger.trace("Process channel " + fixtureModeChannel.getName());

                if (isUnusedChannel(fixtureModeChannel)) {
                    // an unused channel ("null" in the fixture library) still takes its DMX address
                    channelIndex++;
                } else if (fixtureModeChannel.getName() == null || fixtureModeChannel.getName().isEmpty()) {
                    // reference a channel through a pixel matrix

                    List<CachedFixturePixel> availablePixels = getPixelsInOrder(entry.getKey().getProfile(), fixtureModeChannel.getRepeatFor());

                    if (CHANNEL_ORDER_PER_PIXEL.equals(fixtureModeChannel.getChannelOrder())) {
                        // each channel for each pixel

                        for (CachedFixturePixel availablePixel : availablePixels) {
                            for (String modeTemplateChannelName : fixtureModeChannel.getTemplateChannels()) {
                                setUniverseValueForChannel(universe, channelIndex, entry.getKey(), modeTemplateChannelName, availablePixel.getKey(), entry.getValue(), colorDimmer);
                                channelIndex++;
                            }
                        }
                    } else if (CHANNEL_ORDER_PER_CHANNEL.equals(fixtureModeChannel.getChannelOrder())) {
                        // each pixel for each channel

                        for (String modeTemplateChannelName : fixtureModeChannel.getTemplateChannels()) {
                            for (CachedFixturePixel availablePixel : availablePixels) {
                                setUniverseValueForChannel(universe, channelIndex, entry.getKey(), modeTemplateChannelName, availablePixel.getKey(), entry.getValue(), colorDimmer);
                                channelIndex++;
                            }
                        }
                    }
                } else {
                    // direct reference to a channel

                    setUniverseValueForChannel(universe, channelIndex, entry.getKey(), fixtureModeChannel.getName(), null, entry.getValue(), colorDimmer);
                    channelIndex++;
                }
            }
        }
    }

    private void calculateUniverse(long timeMillis) {
        try {
            List<PresetRegionScene> presets = getPresets(timeMillis);
            Map<CachedFixture, List<FixtureChannelValue>> calculatedFixtures = getChannelValues(timeMillis, presets);

            setUniverseValues(calculatedFixtures);
        } catch (Exception e) {
            logger.error("Could not calculate the universe", e);
        }
    }

    private long getCurrentPositionMillis() {
        if (compositionPlayer != null) {
            // We have a compositionPlayer -> use it to sync
            return compositionPlayer.getPositionMillis();
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
        List<CachedFixturePixel> pixelKeys = new ArrayList<>();

        FixtureProfile profile = this.getProfileByUuid(fixture.getProfileUuid());
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
        FixtureProfile profile = this.getProfileByUuid(fixture.getProfileUuid());
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
            cachedFixtures.addAll(fixtureParts);
        }

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
    // looks exactly the same.
    private void migrateProject(Project project) {
        if (project == null) {
            return;
        }

        if (project.getVersion() > SUPPORTED_PROJECT_VERSION) {
            logger.warn("Designer project '" + project.getName() + "' was written with a newer designer (project version " + project.getVersion() + ", this Rocket Show knows version " + SUPPORTED_PROJECT_VERSION + "). Parts of the show may be missing. Please update Rocket Show.");
        }

        migrateProjectToVersion3(project);
        migrateProjectToVersion7(project);
        migrateProjectToVersion8(project);
        migrateProjectToVersion9(project);
        migrateProjectToVersion10(project);
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
                    if (effect instanceof EffectCurve curve && !"square".equals(curve.getCurveType())) {
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
    // "Red 3")
    private static String renameLegacyPixelKeys(String text, List<Integer> pixelCount, boolean whole) {
        Matcher matcher = LEGACY_PIXEL_KEY.matcher(text);
        StringBuilder result = new StringBuilder();

        while (whole ? matcher.matches() : matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));
            boolean inMatrix = x >= 1 && x <= pixelCount.get(0) && y >= 1 && y <= pixelCount.get(1) && z >= 1 && z <= pixelCount.get(2);

            matcher.appendReplacement(result, Matcher.quoteReplacement(inMatrix ? getDefaultPixelKey(pixelCount, x, y, z) : matcher.group()));

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
                    .sorted(Comparator.comparingLong(ScenePlaybackRegion::getStartMillis))
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
                    track.setName(sceneTracks.isEmpty() ? scene.getName() : scene.getName() + " " + (sceneTracks.size() + 1));
                    sceneTracks.add(track);
                    composition.getTracks().add(track);
                }

                region.setTrackUuid(track.getUuid());
            }
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

        if (compositionPlayer != null) {
            this.composition = getCompositionByName(project, compositionPlayer.getComposition().getName());
        }

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
        // The scenes picked by hand on the Play screen aren't the project of a composition: a composition loading or
        // stopping leaves them playing
        if (project == null || playingManualScenes) {
            return;
        }

        stopTimer();

        removeLightingUniverses();

        project = null;
        pipeline = null;

        // Nothing plays a project of its own anymore -> the scenes picked by hand come back
        if (hasManualScenes()) {
            startManualScenes();
        }
    }

    @Override
    public synchronized void playManualScenes(Project project, List<String> sceneUuids, double masterDimmer) {
        manualSceneUuids = new ArrayList<>(sceneUuids);
        manualScenesMasterDimmer = masterDimmer;

        if (playingManualScenes && project == manualScenesProject) {
            // The project plays already: the engine picks the scenes and the master dimmer up with its next universe,
            // without starting the effects over (e.g. while the master dimmer is being dragged)
            selectedSceneUuids = manualSceneUuids;
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

        startPositionMillis = 0;
        startTimer();
    }

    @Override
    public synchronized void stopManualScenes() {
        manualScenesProject = null;
        manualSceneUuids = new ArrayList<>();
        manualScenesMasterDimmer = 1;

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
    public void setStepPreviewRunning(boolean stepPreviewRunning, long stepPreviewStartMillis) {
        this.stepPreviewRunning = stepPreviewRunning;
        this.stepPreviewStartMillis = stepPreviewStartMillis;
    }

    @Override
    public void setSelectedSceneUuids(List<String> selectedSceneUuids) {
        this.selectedSceneUuids = selectedSceneUuids;
    }

    @Override
    public Project getCurrentProject() {
        return project;
    }
}
