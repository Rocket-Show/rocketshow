package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.zip.ZipEntry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
    private boolean playPreview = false;
    private boolean previewPreset = false;
    private String selectedPresetUuid;
    private List<String> selectedSceneUuids = new ArrayList<>();

    private List<LightingUniverse> lightingUniverses = new ArrayList<>();

    // TODO what is a good corePoolSize?
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(20);
    private ScheduledFuture<?> universeSenderHandle;

    private long lastPlayTimeMillis;
    private long lastPositionMillis;

    private List<CachedFixture> cachedFixtures;

    public DefaultDesignerService(SettingsService settingsService, FileFilterService fileFilterService, LightingService lightingService) {
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.lightingService = lightingService;

        if (settingsService.getSettings().getDesignerLivePreview()) {
            startPreview(0);
        }

        this.buildDesignerCache();
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
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
        File file = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath() + File.separator + name + ".json");

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
        String projectName = projectObject.getName();
        String designerPath = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getDesignerPath();

        createDirectoryIfNotExists(designerPath);

        BufferedWriter writer = new BufferedWriter(new FileWriter(designerPath + File.separator + projectName + ".json"));
        writer.write(project);
        writer.close();

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

    private long getCurrentPositionMillis() {
        if (pipeline != null) {
            return pipeline.queryPosition(MILLISECONDS);
        }

        return System.currentTimeMillis() - lastPlayTimeMillis + lastPositionMillis;
    }

    private List<PresetRegionScene> getPresetsInTime(long timeMillis) {
        // Return all presets which should be active during the specified time
        List<PresetRegionScene> activePresets = new ArrayList<>();

        for (int sceneIndex = project.getScenes().size() - 1; sceneIndex >= 0; sceneIndex--) {
            Scene scene = project.getScenes().get(sceneIndex);

            for (ScenePlaybackRegion region : composition.getScenePlaybackRegions()) {
                if (region.getSceneUuid().equals(scene.getUuid())) {
                    for (int presetIndex = project.getPresets().size() - 1; presetIndex >= 0; presetIndex--) {
                        for (String presetUuid : scene.getPresetUuids()) {
                            if (presetUuid.equals(project.getPresets().get(presetIndex).getUuid())) {
                                Preset preset = getPresetByUuid(presetUuid);

                                if (preset != null) {
                                    long presetStartMillis = preset.getStartMillis() == null ? region.getStartMillis() : region.getStartMillis() + preset.getStartMillis();
                                    long presetEndMillis = preset.getEndMillis() == null ? region.getEndMillis() : region.getStartMillis() + preset.getEndMillis();

                                    // extend the running time, if fading is done outside the boundaries
                                    presetStartMillis -= preset.isFadeInPre() ? preset.getFadeInMillis() : 0;
                                    presetEndMillis += preset.isFadeOutPost() ? preset.getFadeOutMillis() : 0;

                                    if (presetStartMillis <= timeMillis && presetEndMillis >= timeMillis) {
                                        activePresets.add(new PresetRegionScene(preset, region, scene));
                                    }
                                }
                            }
                        }
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
            if (previewPreset) {
                // Only preview the selected preset
                if (selectedPresetUuid != null) {
                    presets.add(new PresetRegionScene(getPresetByUuid(selectedPresetUuid), null, null));
                }
            } else {
                // Preview the selected scenes
                for (int sceneIndex = project.getScenes().size() - 1; sceneIndex >= 0; sceneIndex--) {
                    for (String sceneUuid : selectedSceneUuids) {
                        if (sceneUuid.equals(project.getScenes().get(sceneIndex).getUuid())) {
                            for (int presetIndex = project.getPresets().size() - 1; presetIndex >= 0; presetIndex--) {
                                for (String presetUuid : project.getScenes().get(sceneIndex).getPresetUuids()) {
                                    // Loop over the presets in the preset service to retain the preset order
                                    if (presetUuid.equals(project.getPresets().get(presetIndex).getUuid())) {
                                        presets.add(new PresetRegionScene(project.getPresets().get(presetIndex), null, project.getScenes().get(sceneIndex)));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
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

    // Get the fixture index inside the passed preset (used for chasing)
    private Integer getFixtureIndex(Preset preset, String fixtureUuid) {
        int index = 0;
        List<Integer> countedDmxChannels = new ArrayList<>();

        // Loop over the global fixtures to retain the order
        for (Fixture fixture : project.getFixtures()) {
            for (String presetFixtureUuid : preset.getFixtureUuids()) {
                if (presetFixtureUuid.equals(fixture.getUuid())) {
                    if (fixture.getUuid().equals(fixtureUuid)) {
                        return index;
                    }

                    // don't count fixtures on the same channel as already counted ones
                    if (!countedDmxChannels.contains(fixture.getDmxFirstChannel())) {
                        countedDmxChannels.add(fixture.getDmxFirstChannel());
                        index++;
                    }
                    break;
                }
            }
        }

        // Not found in the preset
        return null;
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
            if (mode.getShortName() != null && mode.getShortName().length() > 0) {
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
            double percentage = Integer.parseInt(fixtureChannel.getDefaultValue().replace("%", ""));
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

    private boolean capabilitiesMatch(
            FixtureCapability.FixtureCapabilityType type1,
            FixtureCapability.FixtureCapabilityType type2,
            FixtureCapability.FixtureCapabilityColor color1,
            FixtureCapability.FixtureCapabilityColor color2,
            String wheel1,
            String wheel2,
            String profileUuid1,
            String profileUuid2
    ) {
        // checks, whether two provided capapabilities match
        return type1 == type2
                && (color1 == null || color1 == color2)
                && (wheel1 == null || wheel1.equals(wheel2))
                && (profileUuid1 == null || profileUuid1.equals(profileUuid2));
    }

    private FixtureCapabilityValue getCapabilityValue(Preset preset, FixtureCapability.FixtureCapabilityType capabilityType, FixtureCapability.FixtureCapabilityColor color, String wheel, String profileUuid) {
        for (FixtureCapabilityValue capabilityValue : preset.getFixtureCapabilityValues()) {
            if (capabilitiesMatch(
                    capabilityValue.getType(),
                    capabilityType,
                    capabilityValue.getColor(),
                    color,
                    capabilityValue.getWheel(),
                    wheel,
                    capabilityValue.getProfileUuid(),
                    profileUuid
            )) {
                return capabilityValue;
            }
        }
        return null;
    }

    private FixtureWheel getWheelByName(FixtureProfile profile, String wheelName) {
        return profile.getWheels().getWheels().get(wheelName);
    }

    private List<FixtureWheelSlot> getWheelSlots(FixtureWheel wheel, int slotNumber) {
        // return one slot or two slots, if they are mixed (e.g. slor number 2.5 returns the slots 2 and 3)
        List<FixtureWheelSlot> slots = new ArrayList<>();

        if (wheel == null) {
            return slots;
        }

        if (slotNumber - Math.floor(slotNumber) > 0) {
            // two slots are set
            int number = (int) (Math.floor(slotNumber));
            slots.add(wheel.getSlots().get(number - 1));
            if (number >= 0 && number < wheel.getSlots().size()) {
                slots.add(wheel.getSlots().get(number));
            }
        } else {
            // only one slot is set
            slots.add(wheel.getSlots().get(slotNumber - 1));
        }

        return slots;
    }

    private List<String> getWheelSlotColors(FixtureWheel wheel, int slotNumber) {
        List<String> colors = new ArrayList<>();
        List<FixtureWheelSlot> wheelSlots = getWheelSlots(wheel, slotNumber);

        for (FixtureWheelSlot slot : wheelSlots) {
            if (slot.getColors() != null && slot.getColors().size() > 0) {
                colors.addAll(slot.getColors());
            }
        }

        return colors;
    }

    private Color hexToRgb(String hex) {
        return new Color(
                Double.valueOf(Integer.valueOf(hex.substring(1, 3), 16)),
                Double.valueOf(Integer.valueOf(hex.substring(3, 5), 16)),
                Double.valueOf(Integer.valueOf(hex.substring(5, 7), 16)));
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

    private Color getMixedWheelSlotColor(FixtureWheel wheel, Integer slotNumber) {
        List<String> colors = getWheelSlotColors(wheel, slotNumber);
        List<Color> colorsRgb = new ArrayList<>();

        if (colors.size() == 0) {
            return null;
        }

        for (String color : colors) {
            colorsRgb.add(hexToRgb(color));
        }

        return mixColors(colorsRgb);
    }

    private CachedFixtureCapability getApproximatedColorWheelCapability(Preset preset, CachedFixtureChannel cachedChannel) {
        // return an approximated wheel slot channel capability, if a color or a slot on a different
        // wheel has been selected
        Double colorRed = null;
        Double colorGreen = null;
        Double colorBlue = null;
        double lowestDiff = Double.MAX_VALUE;
        CachedFixtureCapability lowestDiffCapability = null;
        FixtureCapabilityValue capabilityValue;

        capabilityValue = getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Red, null, null);
        if (capabilityValue != null) {
            colorRed = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Green, null, null);
        if (capabilityValue != null) {
            colorGreen = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Blue, null, null);
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
                        Double diff = Math.abs(mixedColor.getRed() - colorRed) + Math.abs(mixedColor.getGreen() - colorGreen) + Math.abs(mixedColor.getBlue() - colorBlue);
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
        for (int i = 0; i < fixtureIndex; i++) {
            CachedFixture calculatedFixture = fixtures.get(i);

            if (calculatedFixture.getFixture().getDmxUniverseUuid().equals(fixtures.get(fixtureIndex).getFixture().getDmxUniverseUuid())
                    && calculatedFixture.getFixture().getDmxFirstChannel() == fixtures.get(fixtureIndex).getFixture().getDmxFirstChannel()) {

                return calculatedFixture;
            }
        }

        return null;
    }

    private double getPresetIntensity(PresetRegionScene preset, long timeMillis) {
        // When fading is in progress (on preset or scene-level), the current preset does not
        // fully cover underlying values.
        // -> 0 = no covering at all, 1 = fully cover (no fading)
        double intensityPercentageScene = 1;
        double intensityPercentagePreset = 1;
        double intensityPercentage = 1;

        if (preset.getRegion() != null && preset.getScene() != null) {
            // Fade out is stronger than fade in (if they overlap)

            // Take away intensity for scene fading
            long sceneStartMillis = preset.getScene().isFadeInPre() ? preset.getRegion().getStartMillis() - preset.getScene().getFadeInMillis() : preset.getRegion().getStartMillis();
            long sceneEndMillis = preset.getScene().isFadeOutPost() ? preset.getRegion().getEndMillis() + preset.getScene().getFadeOutMillis() : preset.getRegion().getEndMillis();

            if (timeMillis > sceneEndMillis - preset.getScene().getFadeOutMillis()
                    && timeMillis < sceneEndMillis) {
                // Scene fades out
                intensityPercentageScene = ((double) (sceneEndMillis - timeMillis)) / ((double) preset.getScene().getFadeOutMillis());
            } else if (timeMillis < sceneStartMillis + preset.getScene().getFadeInMillis()
                    && timeMillis > sceneStartMillis) {
                // Scene fades in
                intensityPercentageScene = ((double) (timeMillis - sceneStartMillis)) / ((double) preset.getScene().getFadeInMillis());
            }
        }

        if (preset.getRegion() != null && preset.getPreset() != null) {
            // Take away intensity for preset fading
            long presetStartMillis = preset.getPreset().getStartMillis() == null ? preset.getRegion().getStartMillis() : preset.getRegion().getStartMillis() + preset.getPreset().getStartMillis();
            long presetEndMillis = preset.getPreset().getEndMillis() == null ? preset.getRegion().getEndMillis() : preset.getRegion().getStartMillis() + preset.getPreset().getEndMillis();

            // extend the running time, if fading is done outside the boundaries
            presetStartMillis -= preset.getPreset().isFadeInPre() ? preset.getPreset().getFadeInMillis() : 0;
            presetEndMillis += preset.getPreset().isFadeOutPost() ? preset.getPreset().getFadeOutMillis() : 0;

            if (timeMillis > presetEndMillis - preset.getPreset().getFadeOutMillis()
                    && timeMillis < presetEndMillis) {
                // Preset fades out
                intensityPercentagePreset = ((double) (presetEndMillis - timeMillis)) / ((double) preset.getPreset().getFadeOutMillis());
            } else if (timeMillis < presetStartMillis + preset.getPreset().getFadeInMillis()
                    && timeMillis > presetStartMillis) {
                // Preset fades in
                intensityPercentagePreset = ((double) (timeMillis - presetStartMillis)) / ((double) preset.getPreset().getFadeInMillis());
            }

            intensityPercentage = intensityPercentageScene * intensityPercentagePreset;
        }

        return intensityPercentage;
    }

    private void mixCapabilityValues(PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage) {
        boolean hasColor = false;

        // mix the preset capability values
        for (FixtureCapabilityValue presetCapabilityValue : preset.getPreset().getFixtureCapabilityValues()) {
            for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                if (cachedChannel.getChannel() != null) {
                    for (CachedFixtureCapability channelCapability : cachedChannel.getCapabilities()) {
                        if (capabilitiesMatch(
                                presetCapabilityValue.getType(),
                                channelCapability.getCapability().getType(),
                                presetCapabilityValue.getColor(),
                                channelCapability.getCapability().getColor(),
                                presetCapabilityValue.getWheel(),
                                channelCapability.getWheelName(),
                                presetCapabilityValue.getProfileUuid(),
                                cachedFixture.getProfile().getUuid()
                        )) {

                            // the capabilities match -> apply the value, if possible
                            if ((presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity ||
                                    presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity)
                                    && presetCapabilityValue.getValuePercentage() != null) {

                                // intensity and colorIntensity (dimmer and color)
                                double valuePercentage = presetCapabilityValue.getValuePercentage();
                                int defaultValue = 0;

                                // brightness property
                                if (cachedChannel.getCapabilities().size() == 1) {
                                    // the only capability in this channel
                                    FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                    fixtureChannelValue.setChannelName(cachedChannel.getName());
                                    fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                    fixtureChannelValue.setValue(cachedChannel.getMaxValue() * valuePercentage);
                                    this.mixChannelValue(values, fixtureChannelValue, intensityPercentage, defaultValue);

                                    if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                        hasColor = true;
                                    }
                                } else {
                                    // more than one capability in the channel
                                    if ("off".equals(channelCapability.getCapability().getBrightness()) && valuePercentage == 0) {
                                        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                        fixtureChannelValue.setChannelName(cachedChannel.getName());
                                        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                        fixtureChannelValue.setValue(channelCapability.getCenterValue());
                                        this.mixChannelValue(values, fixtureChannelValue, intensityPercentage, defaultValue);

                                        if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                            hasColor = true;
                                        }
                                    } else if (("dark".equals(channelCapability.getCapability().getBrightnessStart()) || "off".equals(channelCapability.getCapability().getBrightnessStart())) && "bright".equals(channelCapability.getCapability().getBrightnessEnd())) {
                                        double value = (channelCapability.getCapability().getDmxRange().get(1) - channelCapability.getCapability().getDmxRange().get(0)) * valuePercentage + channelCapability.getCapability().getDmxRange().get(0);
                                        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                        fixtureChannelValue.setChannelName(cachedChannel.getName());
                                        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                        fixtureChannelValue.setValue(value);
                                        this.mixChannelValue(values, fixtureChannelValue, intensityPercentage, defaultValue);

                                        if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                            hasColor = true;
                                        }
                                    }
                                }
                            } else if ((presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Pan
                                    || presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Tilt)
                                    && presetCapabilityValue.getValuePercentage() != null) {

                                FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                fixtureChannelValue.setChannelName(cachedChannel.getName());
                                fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                fixtureChannelValue.setValue(cachedChannel.getMaxValue() * presetCapabilityValue.getValuePercentage());
                                this.mixChannelValue(values, fixtureChannelValue, 1);
                            } else if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.WheelSlot
                                    && channelCapability.getCapability().getSlotNumber().equals(presetCapabilityValue.getSlotNumber())) {

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
                    CachedFixtureCapability capability = getApproximatedColorWheelCapability(preset.getPreset(), cachedChannel);

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

    private void mixChannelValues(PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage) {
        // mix the preset channel values
        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
            if (cachedChannel.getChannel() != null) {
                for (FixtureChannelValue channelValue : preset.getPreset().getFixtureChannelValues()) {
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
            return calculatedFixtures;
        }

        for (int i = 0; i < cachedFixtures.size(); i++) {
            CachedFixture cachedFixture = cachedFixtures.get(i);

            // all values of the current fixture channels
            List<FixtureChannelValue> values = new ArrayList<>();

            CachedFixture alreadyCalculatedFixture = getAlreadyCalculatedFixture(cachedFixtures, i);

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
                    Integer fixtureIndex = getFixtureIndex(preset.getPreset(), cachedFixture.getFixture().getUuid());

                    if (fixtureIndex != null) {
                        // this fixture is also in the preset -> mix the required values (overwrite existing values,
                        // if set multiple times)
                        double intensityPercentage = getPresetIntensity(preset, timeMillis);

                        mixCapabilityValues(preset, cachedFixture, values, intensityPercentage);
                        mixChannelValues(preset, cachedFixture, values, intensityPercentage);
                        mixEffects(timeMillis, fixtureIndex, preset, cachedFixture, values, intensityPercentage);
                    }
                }
            }

            // Store the calculated values for subsequent fixtures on the same DMX address
            calculatedFixtures.put(cachedFixture, values);
        }

        return calculatedFixtures;
    }

    private void mixEffects(long timeMillis, int fixtureIndex, PresetRegionScene preset, CachedFixture cachedFixture, List<FixtureChannelValue> values, double intensityPercentage) {
        for (Effect effect : preset.getPreset().getEffects()) {
            if (effect.isVisible()) {
                // EffectCurve
                if (effect instanceof EffectCurve) {
                    EffectCurve effectCurve = (EffectCurve) effect;

                    // capabilities
                    for (FixtureCapability capability : effectCurve.getCapabilities()) {
                        for (CachedFixtureChannel cachedChannel : cachedFixture.getChannels()) {
                            for (CachedFixtureCapability channelCapability : cachedChannel.getCapabilities()) {
                                if (capabilitiesMatch(
                                        capability.getType(),
                                        channelCapability.getCapability().getType(),
                                        capability.getColor(),
                                        channelCapability.getCapability().getColor(),
                                        null,
                                        null,
                                        null,
                                        null
                                )) {
                                    FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                    fixtureChannelValue.setChannelName(cachedChannel.getName());
                                    fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                    fixtureChannelValue.setValue(cachedChannel.getMaxValue() * effectCurve.getValueAtMillis(timeMillis, fixtureIndex));
                                    mixChannelValue(values, fixtureChannelValue, intensityPercentage);
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
                                        FixtureChannelValue fixtureChannelValue = new FixtureChannelValue();
                                        fixtureChannelValue.setChannelName(cachedChannel.getName());
                                        fixtureChannelValue.setProfileUuid(cachedFixture.getProfile().getUuid());
                                        fixtureChannelValue.setValue(cachedChannel.getMaxValue() * effectCurve.getValueAtMillis(timeMillis, fixtureIndex));
                                        mixChannelValue(values, fixtureChannelValue, intensityPercentage);
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

    private CachedFixtureChannel getChannelByName(CachedFixture fixture, String channelName) {
        for (CachedFixtureChannel channel : fixture.getChannels()) {
            if ((channel.getName() != null && channel.getName().equals(channelName)) || (channel.getChannel() != null && channel.getChannel().getFineChannelAliases().indexOf(channelName) > -1)) {
                return channel;
            }
        }

        return null;
    }

    private void setUniverseValues(Map<CachedFixture, List<FixtureChannelValue>> fixtures, double masterDimmerValue) {
        // Reset all DMX universes
        for (LightingUniverse universe : lightingUniverses) {
            universe.reset();
        }

        // loop over each fixture
        for (Map.Entry<CachedFixture, List<FixtureChannelValue>> entry : fixtures.entrySet()) {
            // TODO Get the correct universe for this fixture
            //Universe universe = getUniverseByUuid(entry.getKey().getFixture().getDmxUniverseUuid());

            // loop over each channel for this fixture
            for (int channelIndex = 0; channelIndex < entry.getKey().getMode().getChannels().size(); channelIndex++) {
                Object channelObj = entry.getKey().getMode().getChannels().get(channelIndex);
                if (channelObj instanceof String) {
                    // direct reference to a channel

                    String channelName = (String) channelObj;

                    // match this mode channel with a channel value
                    for (FixtureChannelValue channelValue : entry.getValue()) {
                        CachedFixtureChannel channel = getChannelByName(entry.getKey(), channelName);
                        if (channel != null && channel.getChannel() != null) {
                            int fineIndex = channel.getChannel().getFineChannelAliases().indexOf(channelName);
                            if (channel.getName().equals(channelValue.getChannelName()) || fineIndex > -1) {
                                int universeChannel = entry.getKey().getFixture().getDmxFirstChannel() + channelIndex;
                                int dmxValue = (int) Math.floor(channelValue.getValue() / Math.pow(256, channel.getChannel().getFineChannelAliases().size() - (fineIndex + 1))) % 256;
                                // TODO use the correct universe
                                if (lightingUniverses.size() > 0) {
                                    lightingUniverses.get(0).getUniverse().put(universeChannel, dmxValue);
                                }
                                break;
                            }
                        }
                    }
                } else if (channelObj instanceof FixtureModeChannel) {
                    // reference a channel through a pixel matrix

                    FixtureModeChannel fixtureModeChannel = (FixtureModeChannel) channelObj;
                    // TODO
                }
            }
        }
    }

    private void calculateUniverse(long timeMillis) {
        try {
            List<PresetRegionScene> presets = getPresets(timeMillis);
            Map<CachedFixture, List<FixtureChannelValue>> calculatedFixtures = getChannelValues(timeMillis, presets);

            // TODO make the dimmer value adjustable and fall back to the project settings
            setUniverseValues(calculatedFixtures, project.getMasterDimmerValue());

//            logger.info(lightingUniverses.get(0).getUniverse().toString());
        } catch (Exception e) {
            logger.error("Could not calculate the universe", e);
        }
    }

    private void startTimer() {
        if (universeSenderHandle != null || settingsService.getSettings().getDesignerFrequencyHertz() == null) {
            return;
        }

        final Runnable universeSender = new Runnable() {
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
                // Calculate and send the current state
                calculateUniverse(getCurrentPositionMillis());
                lightingService.sendExternalSync();
//                }
            }
        };

        universeSenderHandle =
                scheduler.scheduleAtFixedRate(universeSender, 0, 1000 / settingsService.getSettings().getDesignerFrequencyHertz(), MILLISECONDS);
    }

    private void stopTimer() {
        if (universeSenderHandle == null) {
            return;
        }

        universeSenderHandle.cancel(true);
        universeSenderHandle = null;
    }

    private FixtureWheel getColorWheelByChannel(CachedFixtureChannel channel, FixtureProfile profile) {
        for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
            if (channelCapability.isWheelIsColor()) {
                return channelCapability.getWheel();
            }
        }

        return null;
    }

    private List<CachedFixtureChannel> getCachedChannels(FixtureProfile profile, FixtureMode mode) {
        List<CachedFixtureChannel> channels = new ArrayList<>();

        if (mode == null) {
            return channels;
        }

        for (Map.Entry<String, FixtureChannel> entry : profile.getAvailableChannels().getAvailableChannels().entrySet()) {
            for (Object channel : mode.getChannels()) {
                if (channel instanceof String) {
                    // direct reference to a channel

                    String modeChannel = (String) channel;

                    // don't check the fine channels. only add the coarse channel.
                    if (modeChannel.equals(entry.getKey())) {
                        CachedFixtureChannel cachedFixtureChannel = new CachedFixtureChannel();
                        cachedFixtureChannel.setChannel(entry.getValue());
                        cachedFixtureChannel.setName(entry.getKey());
                        cachedFixtureChannel.setCapabilities(getCapabilitiesByChannel(cachedFixtureChannel.getChannel(), entry.getKey(), profile));
                        Double defaultValue = getDefaultValueByChannel(cachedFixtureChannel.getChannel());
                        if (defaultValue != null) {
                            cachedFixtureChannel.setDefaultValue(defaultValue);
                        }
                        cachedFixtureChannel.setMaxValue(getMaxValueByChannel(cachedFixtureChannel.getChannel()));
                        cachedFixtureChannel.setColorWheel(getColorWheelByChannel(cachedFixtureChannel, profile));
                        channels.add(cachedFixtureChannel);
                    }
                } else if (channel instanceof FixtureModeChannel) {
                    // reference a channel through a pixel matrix

                    FixtureModeChannel fixtureModeChannel = (FixtureModeChannel) channel;
                    // TODO
                } else {
                    // null may be passed as a placeholder for an undefined channel
                    channels.add(new CachedFixtureChannel());
                }
            }
        }

        return channels;
    }

    private void updateCachedFixtures() {
        // calculate some frequently used values as a cache to save cpu time
        // afterwards
        cachedFixtures = new ArrayList<>();

        for (int i = 0; i < project.getFixtures().size(); i++) {
            Fixture fixture = project.getFixtures().get(i);
            CachedFixture cachedFixture = new CachedFixture();

            cachedFixture.setFixture(fixture);
            cachedFixture.setProfile(getProfileByUuid(fixture.getProfileUuid()));
            cachedFixture.setMode(getModeByFixture(cachedFixture.getProfile(), fixture));
            cachedFixture.setChannels(getCachedChannels(cachedFixture.getProfile(), cachedFixture.getMode()));

            cachedFixtures.add(cachedFixture);
        }
    }

    @Override
    public void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline) {
        this.compositionPlayer = compositionPlayer;
        this.project = project;
        this.pipeline = pipeline;

        if (compositionPlayer != null) {
            this.composition = getCompositionByName(project, compositionPlayer.getComposition().getName());
        }

        // Create the caches
        updateCachedFixtures();

        // TODO Init all universes
        LightingUniverse newUniverse = new LightingUniverse();
        lightingUniverses.add(newUniverse);

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            lightingService.addLightingUniverse(lightingUniverse);
        }

        lightingService.setExternalSync(true);
        lastPositionMillis = 0;
    }

    @Override
    public void play() {
        if (project == null) {
            return;
        }

        lastPlayTimeMillis = System.currentTimeMillis();
        startTimer();
    }

    @Override
    public void pause() {
        if (project == null) {
            return;
        }

        stopTimer();
        lastPositionMillis = getCurrentPositionMillis();
    }

    @Override
    public void seek(long positionMillis) {
        if (project == null) {
            return;
        }

        if (pipeline != null) {
            // Rely on the pipeline position
            return;
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();
        this.lastPositionMillis = positionMillis;
    }

    @Override
    public void close() {
        if (project == null) {
            return;
        }

        stopTimer();

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            lightingService.removeLightingUniverse(lightingUniverse);
        }

        project = null;
        pipeline = null;
    }

    @Override
    public void startPreview(long positionMillis) {
        if (project == null) {
            return;
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();
        this.lastPositionMillis = positionMillis;

        startTimer();
    }

    @Override
    public void stopPreview() {
        if (project == null) {
            return;
        }

        stopTimer();

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            lightingService.removeLightingUniverse(lightingUniverse);
        }

        lightingUniverses = new ArrayList<>();
    }

    @Override
    public void setPreviewComposition(String compositionName) {
        if (compositionName != null) {
            composition = getCompositionByName(project, compositionName);
            playPreview = true;
        } else {
            this.playPreview = false;
        }
    }

    @Override
    public long getPositionMillis() {
        if (project == null) {
            return 0;
        }

        return getCurrentPositionMillis();
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
    public void setSelectedSceneUuids(List<String> selectedSceneUuids) {
        this.selectedSceneUuids = selectedSceneUuids;
    }

    @Override
    public Project getCurrentProject() {
        return project;
    }
}
