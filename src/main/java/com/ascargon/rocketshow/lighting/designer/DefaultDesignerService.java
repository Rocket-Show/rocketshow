package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultDesignerService implements DesignerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDesignerService.class);

    private final SettingsService settingsService;
    private final FileFilterService fileFilterService;
    private final LightingService lightingService;

    private List<Project> projects;

    // The currently played project, pipeline and player
    private Project project;
    private Pipeline pipeline;
    private CompositionPlayer compositionPlayer;
    private Composition composition;

    // Live preview
    private boolean previewPreset = false;
    private String selectedPresetUuid;
    private List<String> selectedScenesUuid;

    private LightingUniverse lightingUniverse;

    private Timer sendUniverseTimer;

    private long lastPlayTimeMillis;
    private long lastPositionMillis;

    public DefaultDesignerService(SettingsService settingsService, FileFilterService fileFilterService, LightingService lightingService) {
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.lightingService = lightingService;

        this.buildCache();
    }

    private void buildCache() {
        // Load all designer files
        File folder;
        File[] fileList;

        logger.debug("Get all designer projects");

        folder = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath());
        fileList = folder.listFiles();

        projects = new ArrayList<>();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    logger.debug("Found project '" + file.getName() + "'");

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Project project = mapper.readValue(file, Project.class);

                        projects.add(project);
                    } catch (IOException e) {
                        logger.error("Could not load project '" + file.getName() + "'", e);
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
            if (sceneUuid.equals(scene.getUuid())) {
                return scene;
            }
        }

        return null;
    }

    private long getCurrentPositionMillis() {
        if (pipeline != null) {
            return pipeline.queryPosition(TimeUnit.MILLISECONDS);
        }

        return System.currentTimeMillis() - lastPlayTimeMillis + lastPositionMillis;
    }

    private List<PresetRegionScene> getPresetsInTime(long timeMillis) {
        // Return all presets which should be active during the specified time
        List<PresetRegionScene> activePresets = new ArrayList<>();

        for (Scene scene : project.getScenes()) {
            for (ScenePlaybackRegion region : composition.getScenePlaybackRegions()){
                if (region.getStartMillis() <= timeMillis && region.getEndMillis() >= timeMillis) {
                    // This region is currently being played -> check all scene presets
                    for (String presetUuid : scene.getPresetUuids()){
                        Preset preset = getPresetByUuid(presetUuid);

                        if ((preset.getStartMillis() == null || preset.getStartMillis() + region.getStartMillis() <= timeMillis)
                                && (preset.getEndMillis() == null || preset.getEndMillis() + region.getStartMillis() >= timeMillis)) {

                            activePresets.add(new PresetRegionScene(preset, region, scene));
                        }
                    }
                }
            }
        }

        return activePresets;
    }

    private PresetRegionScene[] getPresets(long timeMillis) {
        // Get relevant presets in correct order to process with their corresponding scene, if available
        List<PresetRegionScene> presets = new ArrayList<>();

        if (CompositionPlayer.PlayState.PLAYING.equals(compositionPlayer.getPlayState())) {
            // Only use active presets in current regions
            presets = getPresetsInTime(timeMillis);
        } else {
            if (previewPreset) {
                // Only preview the selected preset
                if (selectedPresetUuid != null) {
                    presets.add(new PresetRegionScene(getPresetByUuid(selectedPresetUuid), null, null));
                }
            } else {
                // Preview the selected scenes
                for (let sceneIndex = scenes.length - 1; sceneIndex >= 0; sceneIndex--) {
                    Scene scene = getSceneByUuid(sceneUuid);

                    for (let presetIndex = this.projectService.project.presets.length - 1; presetIndex >= 0; presetIndex--) {
                        for (let presetUuid of scenes[sceneIndex].presetUuids){
                            // Loop over the presets in the preset service to retain the preset order
                            if (presetUuid == this.projectService.project.presets[presetIndex].uuid) {
                                presets.push(new PresetRegionScene(this.projectService.project.presets[presetIndex], undefined, scenes[sceneIndex]));
                                break;
                            }
                        }
                    }
                }
            }
        }

        return presets.toArray(new PresetRegionScene[presets.size()]);
    }

    private Map<String, FixtureCapabilityValue[]> getFixturePropertyValues(long timeMillis, List<PresetRegionScene> presets) {

    }

    private void calculateUniverse(long timeMillis) {
        // TODO
    }

    private void sendUniverse() {
        // Calculate and send the current state
        calculateUniverse(getCurrentPositionMillis());
        lightingService.sendExternalSync();
    }

    private void startTimer() {
        if (sendUniverseTimer != null) {
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                // TODO
//                if (pipeline == null && getCurrentTimeMillis() > project.duration) {
//                    // There is no Gstreamer pipeline and the designer project finished
//                    try {
//                        compositionPlayer.stop();
//                    } catch (Exception e) {
//                        logger.error("Could not automatically stop the composition from the designer project", e);
//                    }
//                } else {
                sendUniverse();
//                }
            }
        };

        sendUniverseTimer = new Timer();
        sendUniverseTimer.schedule(timerTask, 1000 / settingsService.getSettings().getDesignerFrequencyHertz());
    }

    private void stopTimer() {
        if (sendUniverseTimer == null) {
            return;
        }

        sendUniverseTimer.cancel();
        sendUniverseTimer = null;
    }

    @Override
    public void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline) {
        this.compositionPlayer = compositionPlayer;
        this.project = project;
        this.composition = getCompositionByName(project, compositionPlayer.getComposition().getName());
        lightingUniverse = new LightingUniverse();
        lightingService.addLightingUniverse(lightingUniverse);
        lightingService.setExternalSync(true);
        lastPositionMillis = 0;
    }

    @Override
    public void play() {
        lastPlayTimeMillis = System.currentTimeMillis();
        startTimer();
    }

    @Override
    public void pause() {
        stopTimer();
        lastPositionMillis = getCurrentPositionMillis();
    }

    @Override
    public void seek(long positionMillis) {
        if (pipeline != null) {
            // Rely on the pipeline position
            return;
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();
        this.lastPositionMillis = positionMillis;
    }

    @Override
    public void close() {
        startTimer();

        lightingService.removeLightingUniverse(lightingUniverse);

        lightingUniverse = null;
        project = null;
        pipeline = null;
    }

    @Override
    public long getPositionMillis() {
        return getCurrentPositionMillis();
    }

}
