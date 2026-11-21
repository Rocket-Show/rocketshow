package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.audio.AudioAnalysisService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.engine.EngineRenderer;
import com.ascargon.rocketshow.lighting.engine.LightingEngine;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.AtomicFile;
import com.ascargon.rocketshow.util.FileFilterService;
import com.ascargon.rocketshow.util.FileNames;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The projects of the designer on this unit, and what of them plays: the project of the composition playing, the live
 * preview of the designer and the scenes picked by hand on the Play screen. The shared lighting engine works out the
 * universes (see EngineRenderer and docs/lighting-engine.md): this service hands it what plays and sends what it comes
 * to, with a timer at the frequency of the settings.
 */
@Service
public class DefaultDesignerService implements DesignerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDesignerService.class);

    private final SettingsService settingsService;
    private final FileFilterService fileFilterService;
    private final LightingService lightingService;

    private List<Project> projects;

    // The currently played project, pipeline and player
    private volatile Project project;
    private Pipeline pipeline;
    private volatile CompositionPlayer compositionPlayer;
    private volatile Composition composition;
    private volatile long startPositionMillis = 0;

    // Live preview
    private volatile boolean playPreview = false; // Is the preview playing a composition?
    private volatile boolean previewPreset = false;
    private volatile String selectedPresetUuid;
    private volatile String selectedStepUuid;
    private volatile boolean stepPreviewRunning = false;
    private volatile double stepPreviewStartMillis = 0;
    private volatile List<String> selectedSceneUuids = new ArrayList<>();

    // the holds the timeline of the composition playing went through (see CompositionHold): the clips spanning them ran
    // on while the position stood still
    private volatile List<CompositionHold> holds = new ArrayList<>();

    // the fixtures being identified from the fixture pool of the designer: they blink at full white
    private volatile List<String> identifyFixtureUuids = new ArrayList<>();

    // the DMX channels the faders of the designer hold at a value while it previews (see setDmxOverrides)
    private volatile List<DmxOverride> dmxOverrides = new ArrayList<>();

    // the fixtures the designer asks from its fixture pool to reset themselves or to switch their lamp on
    private volatile List<String> resetFixtureUuids = new ArrayList<>();
    private volatile List<String> lampOnFixtureUuids = new ArrayList<>();

    // The scenes picked by hand on the Play screen (see ScenePlayerService), the project they are from and the master
    // dimmer of the Play screen. They play whenever no composition plays a project of its own: right away, or once the
    // composition closes its project.
    private Project manualScenesProject;
    private volatile List<String> manualSceneUuids = new ArrayList<>();
    private volatile List<String> flashedSceneUuids = new ArrayList<>();
    private volatile double manualScenesMasterDimmer = 1;
    private volatile boolean playingManualScenes = false;

    // the scenes picked by hand were let go (when, in real time): they play until they have faded out, then their
    // lights are turned off
    private volatile boolean releasingManualScenes = false;
    private volatile long releasedMillis = 0;

    // The beats of the tempo playing (see BeatClock): the one from outside (tapped, set or of a MIDI clock, see
    // TempoService), else the programmed one of the composition or the project playing. The lighting engine has a
    // beat clock of its own, which starts where this one is when a project is loaded and is told the same taps.
    private final BeatClock beatClock = new BeatClock(System.currentTimeMillis());
    private volatile Double outsideBeatsPerMinute;

    // the timeline of the composition playing holds at a hold point until GO: since when, in real time (see hold)
    private volatile boolean holding = false;
    private double holdStartRealMillis = 0;

    // the shared lighting engine working out the universes on a thread of its own, and the generation of the frames
    // the timer sends: frames asked for before the timer was started last are not sent (see EngineRenderer.take)
    private final EngineRenderer renderer;
    private final AtomicLong generation = new AtomicLong();

    private List<LightingUniverseState> lightingUniverses = new ArrayList<>();

    // whether the universes registered hold a frame of the engine yet: until then nothing is sent, and what was sent
    // last stays on the wire (e.g. while a project is compiled)
    private boolean universesWritten = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> universeSenderHandle;

    // Held while the timer sends a universe, so stopping the timer waits for a send running at the moment
    private final Object universeSendLock = new Object();

    private long lastPlayTimeMillis;

    // Whether a frame is worked out for the moment it is sent, a period after what plays was taken: the timeline runs
    // on to there, so the lights stay with the audio. Package visible for the tests, which play a composition at a
    // position standing still: without, a frame is worked out for the moment what plays was taken.
    boolean renderAhead = true;

    // the analysis of the audio of the composition playing, which the pixel effects follow
    private final AudioAnalysisService audioAnalysisService;

    public DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService
    ) {
        this(settingsService, fileFilterService, lightingService, new AudioAnalysisService());
    }

    @Autowired
    public DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService,
            AudioAnalysisService audioAnalysisService
    ) {
        this(settingsService, fileFilterService, lightingService, audioAnalysisService, LightingEngine::loadPacked);

        // the engine is loaded and warmed up with the projects of the unit before a show (once in the life of the JVM)
        EngineRenderer.warmUp(LightingEngine::loadPacked, this::getWarmUpProjects);
    }

    // with the lighting engine the passed loader loads (package visible for the tests of a fault of the engine)
    DefaultDesignerService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            LightingService lightingService,
            AudioAnalysisService audioAnalysisService,
            Supplier<LightingEngine> engineLoader
    ) {
        this.audioAnalysisService = audioAnalysisService;
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.lightingService = lightingService;
        this.renderer = new EngineRenderer(engineLoader, audioAnalysisService, beatClock);

        // the taps of the beat clock reach the engine with their moments
        beatClock.setListener(new BeatClock.Listener() {
            @Override
            public void alignBeat(double realMillis) {
                renderer.tap(realMillis);
            }

            @Override
            public void beatOne(boolean jump, double realMillis) {
                renderer.beatOne(realMillis, jump);
            }

            @Override
            public void setBeats(double beats, double realMillis) {
                renderer.setBeats(realMillis, beats);
            }
        });

        if (settingsService.getSettings().getDesignerLivePreview()) {
            startPreview(0);
        }

        this.buildDesignerCache();
    }

    // the projects of the unit as the engine is handed them, read anew from their files (the ones read already may be
    // migrated meanwhile)
    private List<String> getWarmUpProjects() {
        List<String> projectJsons = new ArrayList<>();
        File[] files = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath()).listFiles();

        for (File file : files == null ? new File[0] : files) {
            try {
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    Project warmUp = DesignerJson.MAPPER.readValue(file, Project.class);
                    ProjectMigration.migrate(warmUp);
                    projectJsons.add(DesignerProjectJson.writeForEngine(warmUp));
                }
            } catch (Exception e) {
                logger.debug("Could not read the designer project '{}' to warm the lighting engine up", file.getName(), e);
            }
        }

        return projectJsons;
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

    // When the scene of a clip stops playing: its fade out may reach past the clip
    private static double getSceneEndMillis(Scene scene, ScenePlaybackRegion region) {
        return scene != null && scene.isFadeOutPost() ? region.getEndMillis() + scene.getFadeOutMillis() : region.getEndMillis();
    }

    // When a preset played in the scene of a clip stops playing, its own fade out past its end included. A preset
    // without times of its own plays as long as the scene does, also while the scene fades out past the clip.
    private static double getPresetPlayEndMillis(Preset preset, ScenePlaybackRegion region, Scene scene) {
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

    private static Composition getCompositionByName(Project project, String compositionName) {
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

    // ---- the tempo ----

    // the tempo of the composition playing, or else of the composition open in the designer, like its own preview. Null
    // without either.
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

    @Override
    public BeatClock getBeatClock() {
        return beatClock;
    }

    // the tempo from outside: tapped, set or of a MIDI clock (null = the programmed one, see TempoService)
    @Override
    public void setOutsideBeatsPerMinute(Double beatsPerMinute) {
        long now = System.currentTimeMillis();
        outsideBeatsPerMinute = beatsPerMinute;
        syncBeatClock(now);
        renderer.setTempo(now, beatsPerMinute);
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
    private void syncBeatClock(long nowMillis) {
        Double beatsPerMinute = getTempoBeatsPerMinute();

        if (beatsPerMinute != null && beatsPerMinute > 0) {
            beatClock.setBeatsPerMinute(beatsPerMinute, nowMillis);
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

        updateHold(System.currentTimeMillis());
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

    // the hold going on lasts until the passed moment (also on the timer, which mustn't wait for the lock of this
    // service: loading a project holds it while it stops the timer)
    private void updateHold(long nowMillis) {
        List<CompositionHold> current = holds;

        if (holding && !current.isEmpty()) {
            current.getLast().setHeldMillis(nowMillis - holdStartRealMillis);
        }
    }

    // ---- the audio ----

    // The audio buses the pixel effects of a project follow: the ones they name, all of them for an effect naming none.
    // The engine takes one stream of audio: it is handed these buses added together (see docs/lighting-engine.md).
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

    // Package visible for the tests, which play recorded analyses
    AudioAnalysisService getAudioAnalysisService() {
        return audioAnalysisService;
    }

    // Package visible for the tests
    EngineRenderer getRenderer() {
        return renderer;
    }

    // ---- a frame ----

    // the position at the passed moment of real time
    private double getCurrentPositionMillis(long nowMillis) {
        CompositionPlayer player = compositionPlayer;

        if (player != null) {
            // We have a compositionPlayer -> use it to sync
            return player.getPositionMillis();
        }

        if (holding) {
            // the timeline of the designer holds at the position it is previewed from
            return startPositionMillis;
        }

        // No compositionPlayer, e.g. only designer preview -> just use the current time as reference
        return nowMillis - lastPlayTimeMillis + startPositionMillis;
    }

    // What plays at the passed moment, for the frame worked out for the passed moment, when it is sent: the timeline of
    // the composition playing (also while it holds at a hold point) or previewed, else the scenes picked by hand, else
    // what the designer previews (the preset selected alone, or the scenes selected)
    private EngineRenderer.Frame frame(long nowMillis, double renderMillis) {
        CompositionPlayer player = compositionPlayer;
        boolean playerPlaying = player != null && CompositionPlayer.PlayState.PLAYING.equals(player.getPlayState());
        boolean timeline = playPreview || (player != null && (holding || playerPlaying));
        List<String> scenes = selectedSceneUuids;
        EngineRenderer.Mode mode;

        if (timeline) {
            mode = EngineRenderer.Mode.TIMELINE;
        } else if (playingManualScenes) {
            mode = EngineRenderer.Mode.LIVE;
        } else if (previewPreset || scenes == null || scenes.isEmpty()) {
            mode = EngineRenderer.Mode.PREVIEW_PRESET;
        } else {
            mode = EngineRenderer.Mode.PREVIEW_SCENES;
        }

        // the timeline stands still while it holds, and while the player doesn't play (yet)
        boolean running = !holding && (player == null || playerPlaying);
        Composition playing = composition;

        return new EngineRenderer.Frame(generation.get(), nowMillis, renderMillis, mode == EngineRenderer.Mode.LIVE ? 0 : getCurrentPositionMillis(nowMillis),
                running, mode, playing == null ? null : playing.getName(), player != null, player != null && player.isDefaultComposition(),
                selectedPresetUuid, selectedStepUuid, scenes == null ? List.of() : List.copyOf(scenes), stepPreviewRunning,
                stepPreviewStartMillis, EngineRenderer.copyOf(holds), holding, identifyFixtureUuids, resetFixtureUuids,
                lampOnFixtureUuids, dmxOverrides, playingManualScenes ? manualScenesMasterDimmer : 1, outsideBeatsPerMinute);
    }

    // hand the engine what plays now, for the frame sent after the passed period
    private void requestFrame(long nowMillis, long periodMillis) {
        try {
            syncBeatClock(nowMillis);
            updateHold(nowMillis);
            renderer.frame(frame(nowMillis, renderAhead ? nowMillis + periodMillis : nowMillis));
        } catch (Exception e) {
            logger.error("Could not hand the frame to the lighting engine", e);
        }
    }

    // send the frame the engine worked out for this moment, or what was sent last if it has none
    private void send() {
        EngineRenderer.Rendered rendered = renderer.take(generation.get());

        if (rendered != null) {
            rendered.writeTo(lightingUniverses);
            universesWritten = true;
        }

        if (!universesWritten) {
            // the engine has worked nothing out for this project yet
            return;
        }

        lightingService.sendExternalSync(settingsService.getSettings().getEnableMonitor());

        // the scenes let go have faded out: their lights are turned off (outside this send, which the stop waits for)
        if (rendered != null && rendered.isLiveIdle() && releasingManualScenes && playingManualScenes && rendered.getRenderMillis() >= releasedMillis) {
            releasingManualScenes = false;
            scheduler.execute(this::stopReleasedManualScenes);
        }
    }

    private void startTimer() {
        if (universeSenderHandle != null || settingsService.getSettings().getDesignerFrequencyHertz() == null) {
            logger.debug("Could not start preview timer (universeSenderHandle = {}, designerFrequencyHertz = {})", universeSenderHandle, settingsService.getSettings().getDesignerFrequencyHertz());
            return;
        }

        logger.debug("Start designer universe send timer...");

        lastPlayTimeMillis = System.currentTimeMillis();
        long periodMillis = 1000 / settingsService.getSettings().getDesignerFrequencyHertz();

        // the frames of before aren't sent anymore; the first one is worked out while the timer waits to send it
        generation.incrementAndGet();
        requestFrame(lastPlayTimeMillis, periodMillis);

        // Each period: send the frame worked out for now, and ask for the one of the next period. A frame the engine
        // hasn't finished in time is not waited for (see EngineRenderer).
        final Runnable universeSender = () -> {
            synchronized (universeSendLock) {
                try {
                    send();
                    requestFrame(System.currentTimeMillis(), periodMillis);
                } catch (Exception e) {
                    logger.error("Could not send universe", e);
                }
            }
        };

        logger.debug("Designer universe send timer triggering each {} milliseconds", periodMillis);

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

    // Unregister all universe states of a previous load/preview and forget them. They must not be kept around, because
    // the frames are written to the first state matching a mapping uuid. A stale state would swallow all values, while
    // the currently registered one stays empty.
    private void removeLightingUniverses() {
        for (LightingUniverseState lightingUniverse : lightingUniverses) {
            lightingService.removeLightingUniverse(lightingUniverse);
        }

        lightingUniverses = new ArrayList<>();
        universesWritten = false;
    }

    // ---- what plays ----

    @Override
    public synchronized void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline) {
        // The universes of the project played so far are replaced -> nothing may be sending them anymore, e.g. the
        // scenes picked by hand, which a composition takes the lights over from
        stopTimer();
        playingManualScenes = false;

        // the engine reads projects of the current version
        ProjectMigration.migrate(project);

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
        Set<String> audioBuses = followedBuses(project, settingsService.getSettings().getAudioBusList());
        audioAnalysisService.setRequiredBuses(audioBuses);

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

        generation.incrementAndGet();
        renderer.load(project, System.currentTimeMillis(), new EngineRenderer.Load(audioBuses, outsideBeatsPerMinute));
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
        renderer.unload();

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
            // The project plays already: the engine picks the scenes and the master dimmer up with its next frame,
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
        long now = System.currentTimeMillis();
        syncBeatClock(now);
        renderer.startLive(now);
        updateLiveScenes();

        startPositionMillis = 0;
        startTimer();
    }

    // hand the scenes picked and flashed to the engine, which picks the new ones and lets go of the others
    private void updateLiveScenes() {
        renderer.setLiveScenes(System.currentTimeMillis(), manualSceneUuids, flashedSceneUuids);
    }

    @Override
    public synchronized void releaseManualScenes() {
        if (!playingManualScenes) {
            stopManualScenes();
            return;
        }

        // the scenes fade out, then the timer stops them (see send)
        manualSceneUuids = new ArrayList<>();
        flashedSceneUuids = new ArrayList<>();
        selectedSceneUuids = manualSceneUuids;
        releasedMillis = System.currentTimeMillis();
        updateLiveScenes();
        releasingManualScenes = true;
    }

    // the scenes let go have faded out: stop them, unless scenes were picked again meanwhile
    private synchronized void stopReleasedManualScenes() {
        if (playingManualScenes && manualSceneUuids.isEmpty() && flashedSceneUuids.isEmpty()) {
            stopManualScenes();
        }
    }

    @Override
    public void setLiveSceneDimmers(Map<String, Double> dimmers) {
        renderer.setLiveDimmers(dimmers);
    }

    @Override
    public void setLiveSpeed(double speed) {
        renderer.setLiveSpeed(System.currentTimeMillis(), speed);
    }

    @Override
    public synchronized void stopManualScenes() {
        manualScenesProject = null;
        manualSceneUuids = new ArrayList<>();
        flashedSceneUuids = new ArrayList<>();
        manualScenesMasterDimmer = 1;
        releasingManualScenes = false;

        if (!playingManualScenes) {
            return;
        }

        playingManualScenes = false;
        stopTimer();
        removeLightingUniverses();
        project = null;
        renderer.unload();

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
