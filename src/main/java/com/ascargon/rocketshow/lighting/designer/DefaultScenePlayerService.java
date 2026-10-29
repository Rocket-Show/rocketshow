package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DefaultScenePlayerService implements ScenePlayerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultScenePlayerService.class);

    private final DesignerService designerService;
    private final SessionService sessionService;

    public DefaultScenePlayerService(DesignerService designerService, SessionService sessionService) {
        this.designerService = designerService;
        this.sessionService = sessionService;

        // Light the stage again with the scenes picked before the restart
        try {
            play();
        } catch (Exception e) {
            logger.error("Could not play the scenes picked before the restart", e);
        }
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
        Session session = sessionService.getSession();
        session.setSceneProjectName(projectName);
        session.setActiveSceneUuids(sceneUuids == null ? new ArrayList<>() : new ArrayList<>(sceneUuids));

        save();
    }

    @Override
    public synchronized void setBlackout(boolean blackout) {
        sessionService.getSession().setSceneBlackout(blackout);

        save();
    }

    @Override
    public synchronized void setMasterDimmer(double masterDimmer) {
        sessionService.getSession().setSceneMasterDimmer(Math.max(0, Math.min(1, masterDimmer)));

        save();
    }

    private void save() {
        sessionService.save();
        play();
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
