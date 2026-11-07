package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.midi.ActionTriggerMidi;
import com.ascargon.rocketshow.midi.ActionTriggerMidiControlChange;
import com.ascargon.rocketshow.midi.ActionTriggerMidiNoteOn;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.session.SceneDimmer;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultScenePlayerService implements ScenePlayerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultScenePlayerService.class);

    private final DesignerService designerService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final PlayerService playerService;
    private final SetService setService;
    private final SettingsService settingsService;

    // Turns a scene played for a while off again (see playScene)
    private final ScheduledExecutorService sceneEndScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scene-end");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> sceneEnd;

    // the scenes flashed at the moment (not remembered over a restart: nothing holds them anymore then)
    private List<String> flashedSceneUuids = new ArrayList<>();

    // the beats tapped last (real time), the tempo they or a MIDI clock gave and where the tempo is from
    private final List<Long> taps = new ArrayList<>();
    private Double clockBeatsPerMinute;
    private String tempoSource = "project";

    // the clock the taps are timed and the learning waits by (another one in the tests)
    private java.util.function.LongSupplier clock = System::currentTimeMillis;

    // a tap after this long starts tapping anew, and how many taps the tempo is taken from at the most
    private static final long TAP_TIMEOUT_MILLIS = 2000;
    private static final int TAP_COUNT = 8;

    // the tempi and speeds the scenes can run at
    private static final double MIN_BEATS_PER_MINUTE = 20;
    private static final double MAX_BEATS_PER_MINUTE = 300;
    private static final double MIN_SPEED = 0.1;
    private static final double MAX_SPEED = 4;

    // the scene waiting for its key, pad or button of a MIDI controller, and how long it waits at the most
    private SceneLearning learning;
    private long learningSinceMillis;
    private static final long LEARN_TIMEOUT_MILLIS = 60000;

    // Counts the scenes picked, so the end of a scene played for a while knows whether other ones were picked since
    private long selection = 0;

    // Lazy load the notification and player service to avoid a circular dependency: they tell the apps the state of
    // the device, which includes the scenes played here.
    public DefaultScenePlayerService(DesignerService designerService, SessionService sessionService, @Lazy NotificationService notificationService, @Lazy PlayerService playerService, SetService setService, SettingsService settingsService) {
        this.designerService = designerService;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.setService = setService;
        this.settingsService = settingsService;

        if (sessionService.getSession().getSceneBeatsPerMinute() != null) {
            tempoSource = "set";
        }

        // Light the stage again with the scenes picked before the restart
        try {
            play();
        } catch (Exception e) {
            logger.error("Could not play the scenes picked before the restart", e);
        }
    }

    // the clock the taps are timed by (for the tests)
    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    @PreDestroy
    public void close() {
        sceneEndScheduler.shutdownNow();
    }

    @Override
    public List<SceneProject> getProjects() {
        List<SceneProject> sceneProjects = new ArrayList<>();

        // A copy: a project saved meanwhile replaces its cached one
        for (Project project : new ArrayList<>(designerService.getAllProjects())) {
            if (project.getName() != null && project.getScenes() != null && !project.getScenes().isEmpty()) {
                sceneProjects.add(new SceneProject(project));
            }
        }

        sceneProjects.sort(Comparator.comparing(SceneProject::getName, String.CASE_INSENSITIVE_ORDER));

        return sceneProjects;
    }

    @Override
    public String getProjectName() {
        return sessionService.getSession().getSceneProjectName();
    }

    @Override
    public List<String> getActiveSceneUuids() {
        List<String> sceneUuids = sessionService.getSession().getActiveSceneUuids();

        return sceneUuids == null ? new ArrayList<>() : new ArrayList<>(sceneUuids);
    }

    @Override
    public boolean isBlackout() {
        return sessionService.getSession().isSceneBlackout();
    }

    @Override
    public double getMasterDimmer() {
        return sessionService.getSession().getSceneMasterDimmer();
    }

    @Override
    public boolean isHeld() {
        return designerService.hasManualScenes() && !designerService.isPlayingManualScenes();
    }

    @Override
    public synchronized void setActiveScenes(String projectName, List<String> sceneUuids) {
        // Picked anew: a scene played for a while so far doesn't end them
        selection++;
        if (sceneEnd != null) {
            sceneEnd.cancel(false);
            sceneEnd = null;
        }

        Session session = sessionService.getSession();
        session.setSceneProjectName(projectName);
        session.setActiveSceneUuids(sceneUuids == null ? new ArrayList<>() : new ArrayList<>(sceneUuids));

        save();
    }

    @Override
    public synchronized void playScene(String projectName, String scene, long durationMillis) {
        Scene foundScene = findScene(projectName, scene);

        if (foundScene == null) {
            throw new IllegalArgumentException("The designer project '" + projectName + "' has no scene '" + scene + "'");
        }

        setActiveScenes(projectName, List.of(foundScene.getUuid()));

        if (durationMillis > 0) {
            long playedSelection = selection;
            sceneEnd = sceneEndScheduler.schedule(() -> endScene(playedSelection), durationMillis, TimeUnit.MILLISECONDS);
        }
    }

    // The scene of the project with the passed uuid, or else with the passed name
    private Scene findScene(String projectName, String scene) {
        Project project = projectName == null ? null : designerService.getProjectByName(projectName);

        if (project == null || project.getScenes() == null || scene == null) {
            return null;
        }

        for (Scene candidate : project.getScenes()) {
            if (scene.equals(candidate.getUuid())) {
                return candidate;
            }
        }

        for (Scene candidate : project.getScenes()) {
            if (scene.equals(candidate.getName())) {
                return candidate;
            }
        }

        return null;
    }

    // The time of a scene played for a while is up: turn it off, unless other scenes were picked meanwhile
    private synchronized void endScene(long playedSelection) {
        if (playedSelection != selection) {
            return;
        }

        setActiveScenes(getProjectName(), List.of());
    }

    @Override
    public synchronized void releaseAll() {
        flashedSceneUuids = new ArrayList<>();
        setActiveScenes(getProjectName(), List.of());
    }

    @Override
    public synchronized void setBlackout(boolean blackout) {
        sessionService.getSession().setSceneBlackout(blackout);

        save();
    }

    @Override
    public synchronized void toggleBlackout() {
        setBlackout(!isBlackout());
    }

    @Override
    public synchronized void setMasterDimmer(double masterDimmer) {
        sessionService.getSession().setSceneMasterDimmer(Math.max(0, Math.min(1, masterDimmer)));

        save();
    }

    private void save() {
        sessionService.save();
        play();

        // Every app shows the scenes on, also ones picked by an action or turned off at the end of their time
        try {
            notificationService.notifyClients(playerService, setService);
        } catch (Exception e) {
            logger.error("Could not notify the clients about the scenes", e);
        }
    }

    // Hand the picked scenes to the engine, or stop them when nothing is left to play. A blackout without scenes still
    // plays: it keeps the stage dark, also over the lights of the default composition.
    private void play() {
        Session session = sessionService.getSession();
        List<String> sceneUuids = getActiveSceneUuids();
        List<String> flashed = getFlashedSceneUuids();
        Project project = session.getSceneProjectName() == null ? null : designerService.getProjectByName(session.getSceneProjectName());

        if (project == null) {
            designerService.stopManualScenes();
            return;
        }

        designerService.setLiveSceneDimmers(getSceneDimmers());
        designerService.setLiveSpeed(session.getSceneSpeed());
        designerService.setLiveBeatsPerMinute(getLiveBeatsPerMinute(), false);

        if (sceneUuids.isEmpty() && flashed.isEmpty() && !session.isSceneBlackout()) {
            // the scenes fade out, then their lights are turned off
            designerService.releaseManualScenes();
            return;
        }

        designerService.playManualScenes(project, sceneUuids, flashed, session.isSceneBlackout() ? 0 : session.getSceneMasterDimmer());
    }

    // the tempo played live: the one of a MIDI clock, tapped or set (null = the one of the project)
    private Double getLiveBeatsPerMinute() {
        return "clock".equals(tempoSource) && clockBeatsPerMinute != null ? clockBeatsPerMinute : sessionService.getSession().getSceneBeatsPerMinute();
    }

    // tell every app what changed, without saving the session
    private void notifyClients() {
        try {
            notificationService.notifyClients(playerService, setService);
        } catch (Exception e) {
            logger.error("Could not notify the clients about the scenes", e);
        }
    }

    // ---- playing the scenes live ----

    @Override
    public synchronized void toggleScene(String projectName, String scene) {
        Scene foundScene = findScene(projectName, scene);

        if (foundScene == null) {
            throw new IllegalArgumentException("The designer project '" + projectName + "' has no scene '" + scene + "'");
        }

        boolean on = projectName.equals(getProjectName()) && getActiveSceneUuids().contains(foundScene.getUuid());
        setActiveScenes(projectName, on ? List.of() : List.of(foundScene.getUuid()));
    }

    @Override
    public synchronized void flashScene(String projectName, String scene, boolean on) {
        Scene foundScene = findScene(projectName, scene);

        if (foundScene == null) {
            throw new IllegalArgumentException("The designer project '" + projectName + "' has no scene '" + scene + "'");
        }

        if (on && !projectName.equals(getProjectName())) {
            // a scene of another project: the scenes of the project played so far go
            flashedSceneUuids = new ArrayList<>();
            setActiveScenes(projectName, List.of());
        }

        List<String> flashed = new ArrayList<>(flashedSceneUuids);

        if (on && !flashed.contains(foundScene.getUuid())) {
            flashed.add(foundScene.getUuid());
        } else if (!on) {
            flashed.remove(foundScene.getUuid());
        }

        if (flashed.equals(flashedSceneUuids)) {
            return;
        }

        flashedSceneUuids = flashed;
        play();
        notifyClients();
    }

    @Override
    public synchronized List<String> getFlashedSceneUuids() {
        return new ArrayList<>(flashedSceneUuids);
    }

    @Override
    public synchronized void stepScene(String projectName, String folder, int direction) {
        String name = projectName == null || projectName.isEmpty() ? getProjectName() : projectName;
        Project project = name == null ? null : designerService.getProjectByName(name);

        if (project == null) {
            throw new IllegalArgumentException("There is no designer project '" + name + "'");
        }

        String folderUuid = findFolderUuid(project, folder);
        List<Scene> scenes = scenesIn(project, folderUuid);

        if (scenes.isEmpty()) {
            return;
        }

        // the scene on last of the ones stepped through
        int current = -1;

        if (name.equals(getProjectName())) {
            for (String sceneUuid : getActiveSceneUuids()) {
                for (int i = 0; i < scenes.size(); i++) {
                    if (scenes.get(i).getUuid().equals(sceneUuid)) {
                        current = i;
                    }
                }
            }
        }

        int next;

        if (current < 0) {
            next = direction >= 0 ? 0 : scenes.size() - 1;
        } else {
            next = Math.max(0, Math.min(scenes.size() - 1, current + (direction >= 0 ? 1 : -1)));
        }

        setActiveScenes(name, List.of(scenes.get(next).getUuid()));
    }

    // the folder with the passed uuid or name (null = none)
    private static String findFolderUuid(Project project, String folder) {
        if (folder == null || folder.isEmpty() || project.getSceneFolders() == null) {
            return null;
        }

        for (SceneFolder sceneFolder : project.getSceneFolders()) {
            if (folder.equals(sceneFolder.getUuid())) {
                return sceneFolder.getUuid();
            }
        }

        for (SceneFolder sceneFolder : project.getSceneFolders()) {
            if (folder.equals(sceneFolder.getName())) {
                return sceneFolder.getUuid();
            }
        }

        throw new IllegalArgumentException("The designer project '" + project.getName() + "' has no folder '" + folder + "'");
    }

    // The scenes of a folder and all folders inside it (null = all scenes), in the order the designer shows them: the
    // folders and the scenes of a parent ordered together, like the Play screen shows them
    static List<Scene> scenesIn(Project project, String folderUuid) {
        List<Scene> scenes = new ArrayList<>();
        List<Object> children = new ArrayList<>();

        if (project.getSceneFolders() != null) {
            for (SceneFolder sceneFolder : project.getSceneFolders()) {
                if (Objects.equals(emptyToNull(sceneFolder.getParentUuid()), folderUuid)) {
                    children.add(sceneFolder);
                }
            }
        }

        if (project.getScenes() != null) {
            for (Scene scene : project.getScenes()) {
                if (Objects.equals(emptyToNull(scene.getFolderUuid()), folderUuid)) {
                    children.add(scene);
                }
            }
        }

        // a stable sort, like the one of the Play screen
        children.sort(Comparator.comparingDouble(child -> child instanceof SceneFolder sceneFolder ? sceneFolder.getSortIndex() : ((Scene) child).getSortIndex()));

        for (Object child : children) {
            if (child instanceof SceneFolder sceneFolder) {
                scenes.addAll(scenesIn(project, sceneFolder.getUuid()));
            } else {
                scenes.add((Scene) child);
            }
        }

        return scenes;
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    @Override
    public synchronized void tapTempo() {
        long now = clock.getAsLong();

        if (!taps.isEmpty() && now - taps.getLast() > TAP_TIMEOUT_MILLIS) {
            taps.clear();
        }

        taps.add(now);

        while (taps.size() > TAP_COUNT) {
            taps.removeFirst();
        }

        if (taps.size() < 2) {
            // the first beat only falls on the beat
            designerService.setLiveBeatsPerMinute(getLiveBeatsPerMinute(), true);
            return;
        }

        double beatMillis = (double) (taps.getLast() - taps.getFirst()) / (taps.size() - 1);
        double beatsPerMinute = Math.max(MIN_BEATS_PER_MINUTE, Math.min(MAX_BEATS_PER_MINUTE, 60000 / beatMillis));

        tempoSource = "tap";
        sessionService.getSession().setSceneBeatsPerMinute(Math.round(beatsPerMinute * 10) / 10.0);
        sessionService.save();
        designerService.setLiveBeatsPerMinute(getLiveBeatsPerMinute(), true);
        notifyClients();
    }

    @Override
    public synchronized void setBeatsPerMinute(Double beatsPerMinute) {
        taps.clear();
        tempoSource = beatsPerMinute == null ? "project" : "set";
        sessionService.getSession().setSceneBeatsPerMinute(beatsPerMinute == null ? null : Math.max(MIN_BEATS_PER_MINUTE, Math.min(MAX_BEATS_PER_MINUTE, beatsPerMinute)));
        sessionService.save();
        designerService.setLiveBeatsPerMinute(getLiveBeatsPerMinute(), false);
        notifyClients();
    }

    @Override
    public synchronized void setClockTempo(double beatsPerMinute, boolean onTheBeat) {
        if (beatsPerMinute < MIN_BEATS_PER_MINUTE || beatsPerMinute > MAX_BEATS_PER_MINUTE) {
            return;
        }

        // the apps are only told of a tempo changed by a whole beat per minute: a clock ticks all the time
        boolean told = "clock".equals(tempoSource) && clockBeatsPerMinute != null && Math.round(clockBeatsPerMinute) == Math.round(beatsPerMinute);

        tempoSource = "clock";
        clockBeatsPerMinute = beatsPerMinute;
        designerService.setLiveBeatsPerMinute(beatsPerMinute, onTheBeat);

        if (!told) {
            notifyClients();
        }
    }

    @Override
    public synchronized Double getBeatsPerMinute() {
        Double live = getLiveBeatsPerMinute();
        return live != null ? live : designerService.getLiveReferenceBeatsPerMinute();
    }

    @Override
    public synchronized String getTempoSource() {
        return getLiveBeatsPerMinute() == null ? "project" : tempoSource;
    }

    @Override
    public synchronized void setSpeed(double speed) {
        sessionService.getSession().setSceneSpeed(Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed)));
        save();
    }

    @Override
    public double getSpeed() {
        return sessionService.getSession().getSceneSpeed();
    }

    @Override
    public synchronized void setSceneDimmer(String sceneUuid, double dimmer) {
        double value = Math.max(0, Math.min(1, dimmer));
        List<SceneDimmer> dimmers = sessionService.getSession().getSceneDimmers();

        dimmers.removeIf(sceneDimmer -> sceneDimmer.getSceneUuid().equals(sceneUuid));

        // a scene at full needs no dimmer
        if (value < 1) {
            dimmers.add(new SceneDimmer(sceneUuid, value));
        }

        save();
    }

    @Override
    public synchronized Map<String, Double> getSceneDimmers() {
        Map<String, Double> dimmers = new HashMap<>();

        for (SceneDimmer sceneDimmer : sessionService.getSession().getSceneDimmers()) {
            dimmers.put(sceneDimmer.getSceneUuid(), sceneDimmer.getDimmer());
        }

        return dimmers;
    }

    // ---- MIDI learn ----

    @Override
    public synchronized void learn(String projectName, String scene, String mode) {
        Scene foundScene = findScene(projectName, scene);

        if (foundScene == null) {
            throw new IllegalArgumentException("The designer project '" + projectName + "' has no scene '" + scene + "'");
        }

        learning = new SceneLearning(projectName, foundScene.getUuid(), "flash".equals(mode) ? "flash" : "toggle");
        learningSinceMillis = clock.getAsLong();
        notifyClients();
    }

    @Override
    public synchronized void cancelLearn() {
        if (learning != null) {
            learning = null;
            notifyClients();
        }
    }

    @Override
    public synchronized SceneLearning getLearning() {
        if (learning != null && clock.getAsLong() - learningSinceMillis > LEARN_TIMEOUT_MILLIS) {
            learning = null;
        }

        return learning;
    }

    @Override
    public synchronized boolean learnMidi(ShortMessage shortMessage) {
        SceneLearning sceneLearning = getLearning();

        if (sceneLearning == null || shortMessage.getStatus() >= 0xF0 || shortMessage.getData2() == 0) {
            // nothing waits, or a message no key, pad or button sends when it is pressed (e.g. a clock or a release)
            return false;
        }

        ActionTriggerMidi trigger;

        if (shortMessage.getCommand() == ShortMessage.NOTE_ON) {
            ActionTriggerMidiNoteOn noteOn = new ActionTriggerMidiNoteOn();
            noteOn.setNote(shortMessage.getData1());
            trigger = noteOn;
        } else if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE) {
            // the value the button sends when pressed: it sends another one (0) when it is let go
            ActionTriggerMidiControlChange controlChange = new ActionTriggerMidiControlChange();
            controlChange.setController(shortMessage.getData1());
            controlChange.setValue(shortMessage.getData2());
            trigger = controlChange;
        } else {
            return false;
        }

        trigger.setChannel(shortMessage.getChannel());

        ActionScene actionScene = new ActionScene();
        actionScene.setSceneActionType("flash".equals(sceneLearning.getMode()) ? ActionScene.SceneActionType.FLASH_SCENE : ActionScene.SceneActionType.TOGGLE_SCENE);
        actionScene.setProjectName(sceneLearning.getProjectName());
        actionScene.setScene(sceneLearning.getScene());
        actionScene.setUntilStopped(true);
        trigger.getActionList().add(actionScene);

        // A control plays one scene, and a scene is played by one control in each way: what the control played and
        // what played the scene in this way before are forgotten (the ones learned on the Play screen only)
        List<ActionTriggerMidi> triggers = settingsService.getSettings().getActionTriggerMidiList();
        triggers.removeIf(existing -> {
            ActionScene learned = getLearnedAction(existing);
            return learned != null && (sameControl(existing, trigger) || (sceneLearning.getProjectName().equals(learned.getProjectName())
                    && sceneLearning.getScene().equals(learned.getScene()) && learned.getSceneActionType() == actionScene.getSceneActionType()));
        });
        triggers.add(trigger);

        try {
            settingsService.save();
        } catch (Exception e) {
            logger.error("Could not save the MIDI control learned for a scene", e);
        }

        learning = null;
        notifyClients();
        return true;
    }

    // the scene action of a trigger learned on the Play screen: a note or a control change with nothing else to do
    // than to toggle or to flash a scene (null for any other trigger)
    private static ActionScene getLearnedAction(ActionTriggerMidi trigger) {
        if (!(trigger instanceof ActionTriggerMidiNoteOn) && !(trigger instanceof ActionTriggerMidiControlChange)) {
            return null;
        }

        if (trigger.getActionList().size() != 1 || !(trigger.getActionList().getFirst() instanceof ActionScene actionScene)) {
            return null;
        }

        if (actionScene.getSceneActionType() != ActionScene.SceneActionType.TOGGLE_SCENE && actionScene.getSceneActionType() != ActionScene.SceneActionType.FLASH_SCENE) {
            return null;
        }

        return actionScene;
    }

    // whether two triggers are the same key, pad or button
    private static boolean sameControl(ActionTriggerMidi trigger1, ActionTriggerMidi trigger2) {
        if (!Objects.equals(trigger1.getChannel(), trigger2.getChannel())) {
            return false;
        }

        if (trigger1 instanceof ActionTriggerMidiNoteOn noteOn1 && trigger2 instanceof ActionTriggerMidiNoteOn noteOn2) {
            return Objects.equals(noteOn1.getNote(), noteOn2.getNote());
        }

        if (trigger1 instanceof ActionTriggerMidiControlChange controlChange1 && trigger2 instanceof ActionTriggerMidiControlChange controlChange2) {
            return Objects.equals(controlChange1.getController(), controlChange2.getController());
        }

        return false;
    }

    @Override
    public synchronized List<SceneMidiMapping> getMidiMappings(String projectName) {
        List<SceneMidiMapping> mappings = new ArrayList<>();

        for (ActionTriggerMidi trigger : settingsService.getSettings().getActionTriggerMidiList()) {
            ActionScene learned = getLearnedAction(trigger);

            if (learned == null || !Objects.equals(projectName, learned.getProjectName())) {
                continue;
            }

            String mode = learned.getSceneActionType() == ActionScene.SceneActionType.FLASH_SCENE ? "flash" : "toggle";

            if (trigger instanceof ActionTriggerMidiNoteOn noteOn) {
                mappings.add(new SceneMidiMapping(learned.getScene(), mode, "note", trigger.getChannel(), noteOn.getNote()));
            } else if (trigger instanceof ActionTriggerMidiControlChange controlChange) {
                mappings.add(new SceneMidiMapping(learned.getScene(), mode, "cc", trigger.getChannel(), controlChange.getController()));
            }
        }

        return mappings;
    }

    @Override
    public synchronized void forgetMidi(String projectName, String scene, String mode) {
        ActionScene.SceneActionType type = "flash".equals(mode) ? ActionScene.SceneActionType.FLASH_SCENE : ActionScene.SceneActionType.TOGGLE_SCENE;
        boolean removed = settingsService.getSettings().getActionTriggerMidiList().removeIf(trigger -> {
            ActionScene learned = getLearnedAction(trigger);
            return learned != null && Objects.equals(projectName, learned.getProjectName()) && Objects.equals(scene, learned.getScene())
                    && learned.getSceneActionType() == type;
        });

        if (!removed) {
            return;
        }

        try {
            settingsService.save();
        } catch (Exception e) {
            logger.error("Could not save the MIDI controls of the scenes", e);
        }

        notifyClients();
    }

}
