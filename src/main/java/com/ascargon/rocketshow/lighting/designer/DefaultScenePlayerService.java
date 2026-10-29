package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.session.SessionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    // Turns a scene played for a while off again (see playScene)
    private final ScheduledExecutorService sceneEndScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scene-end");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> sceneEnd;

    // Counts the scenes picked, so the end of a scene played for a while knows whether other ones were picked since
    private long selection = 0;

    // Lazy load the notification and player service to avoid a circular dependency: they tell the apps the state of
    // the device, which includes the scenes played here.
    public DefaultScenePlayerService(DesignerService designerService, SessionService sessionService, @Lazy NotificationService notificationService, @Lazy PlayerService playerService, SetService setService) {
        this.designerService = designerService;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.setService = setService;

        // Light the stage again with the scenes picked before the restart
        try {
            play();
        } catch (Exception e) {
            logger.error("Could not play the scenes picked before the restart", e);
        }
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
    public void releaseAll() {
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
        Project project = session.getSceneProjectName() == null ? null : designerService.getProjectByName(session.getSceneProjectName());

        if (project == null || (sceneUuids.isEmpty() && !session.isSceneBlackout())) {
            designerService.stopManualScenes();
            return;
        }

        designerService.playManualScenes(project, sceneUuids, session.isSceneBlackout() ? 0 : session.getSceneMasterDimmer());
    }

}
