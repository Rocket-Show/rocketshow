package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.IntegerProperty;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
    private List<String> selectedSceneUuids = new ArrayList<>();

    private List<LightingUniverse> lightingUniverses = new ArrayList<>();

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

    private long getCurrentPositionMillis() {
        if (pipeline != null) {
            return pipeline.queryPosition(TimeUnit.MILLISECONDS);
        }

        return System.currentTimeMillis() - lastPlayTimeMillis + lastPositionMillis;
    }

    private List<PresetRegionScene> getPresetsInTime(long timeMillis) {
        // Return all presets which should be active during the specified time
        List<PresetRegionScene> activePresets = new ArrayList<>();

        for (int sceneIndex = project.getScenes().length - 1; sceneIndex >= 0; sceneIndex--) {
            Scene scene = project.getScenes()[sceneIndex];

            for (ScenePlaybackRegion region : composition.getScenePlaybackRegions()) {
                if (region.getSceneUuid().equals(scene.getUuid()) && region.getStartMillis() <= timeMillis && region.getEndMillis() >= timeMillis) {
                    // This region is currently being played -> check all scene presets
                    for (int presetIndex = project.getPresets().length - 1; presetIndex >= 0; presetIndex--) {
                        for (String presetUuid : scene.getPresetUuids()) {
                            if (presetUuid.equals(project.getPresets()[presetIndex].getUuid())) {
                                Preset preset = getPresetByUuid(presetUuid);

                                if (preset != null) {
                                    if ((preset.getStartMillis() == null || preset.getStartMillis() + region.getStartMillis() <= timeMillis)
                                            && (preset.getEndMillis() == null || preset.getEndMillis() + region.getStartMillis() >= timeMillis)) {

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

        if (project == null) {
            return presets;
        }

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
                for (int sceneIndex = project.getScenes().length - 1; sceneIndex >= 0; sceneIndex--) {
                    for (String sceneUuid : selectedSceneUuids) {
                        if (sceneUuid.equals(project.getScenes()[sceneIndex].getUuid())) {
                            for (int presetIndex = project.getPresets().length - 1; presetIndex >= 0; presetIndex--) {
                                for (String presetUuid : project.getScenes()[sceneIndex].getPresetUuids()) {
                                    // Loop over the presets in the preset service to retain the preset order
                                    if (presetUuid.equals(project.getPresets()[presetIndex].getUuid())) {
                                        presets.add(new PresetRegionScene(project.getPresets()[presetIndex], null, null));
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

    private void mixChannelValue(List<FixtureChannelValue> existingChannelValues, FixtureChannelValue channelValue, double intensityPercentage) {
        double newValue = channelValue.getValue();
        double existingValue = 0;

        if (intensityPercentage < 1) {
            // We need to mix a possibly existing value (or the default value 0) with the new value (fading)

            // Get the existant value for this property
            for (FixtureChannelValue existingChannelValue : existingChannelValues) {
                if (existingChannelValue.getChannelName().equals(channelValue.getChannelName()) && existingChannelValue.getFixtureTemplateUuid().equals(channelValue.getFixtureTemplateUuid())) {
                    existingValue = existingChannelValue.getValue();
                    break;
                }
            }

            // Mix the existing value with the new value
            newValue = existingValue * (1 - intensityPercentage) + newValue * intensityPercentage;
        }

        // Remove the existant value, if available
        Iterator<FixtureChannelValue> iterator = existingChannelValues.iterator();
        while (iterator.hasNext()) {
            FixtureChannelValue fixtureChannelValue = iterator.next();
            if (fixtureChannelValue.getChannelName().equals(channelValue.getChannelName()) && fixtureChannelValue.getFixtureTemplateUuid().equals(channelValue.getFixtureTemplateUuid())) {
                iterator.remove();
                break;
            }
        }

        // Add the new value
        existingChannelValues.add(new FixtureChannelValue(channelValue.getChannelName(), channelValue.getFixtureTemplateUuid(), newValue));
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

    private FixtureTemplate getTemplateByUuid(String uuid) {
        if (project == null) {
            return null;
        }

        for (FixtureTemplate fixtureTemplate : project.getFixtureTemplates()) {
            if (fixtureTemplate.getUuid().equals(uuid)) {
                return fixtureTemplate;
            }
        }

        return null;
    }

    private FixtureTemplate getTemplateByFixture(Fixture fixture) {
        return getTemplateByUuid(fixture.getFixtureTemplateUuid());
    }

    private FixtureMode getModeByFixture(Fixture fixture) {
        FixtureTemplate template = this.getTemplateByFixture(fixture);

        for (FixtureMode mode : template.getModes()) {
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
        return Math.pow(256, 1 + fixtureChannel.getFineChannelAliases().length) - 1;
    }

    private Double getDefaultValueByChannel(FixtureChannel fixtureChannel) {
        if (fixtureChannel.getDefaultValue() == null) {
            return null;
        }

        if (!isNumeric(fixtureChannel.getDefaultValue()) && fixtureChannel.getDefaultValue().endsWith("%")) {
            // percentage value
            double percentage = Integer.parseInt(fixtureChannel.getDefaultValue().replace("%", ""));
            return 255 / 100d * percentage;
        } else {
            // DMX value
            return Double.parseDouble(fixtureChannel.getDefaultValue()) / getMaxValueByChannel(fixtureChannel) * 255;
        }
    }

    private Fixture getAlreadyCalculatedFixture(Fixture[] fixtures, int fixtureIndex) {
        // Has this fixture already been calculated (same universe and dmx start address as a fixture before)
        // --> return it
        for (int i = 0; i < fixtureIndex; i++) {
            Fixture calculatedFixture = fixtures[i];

            if (calculatedFixture.getDmxUniverseUuid().equals(fixtures[fixtureIndex].getDmxUniverseUuid())
                    && calculatedFixture.getDmxFirstChannel() == fixtures[fixtureIndex].getDmxFirstChannel()) {

                return calculatedFixture;
            }
        }

        return null;
    }

    private List<FixtureChannelFineIndex> getChannelsByTemplateMode(FixtureTemplate template, FixtureMode mode) {
        List<FixtureChannelFineIndex> channels = new ArrayList<>();

        for (Object channel : mode.getChannels()) {
            // Check for string channel. It can get creepy for matrix modes
            if (channel instanceof String) {
                String modeString = (String) channel;

                for (Map.Entry<String, FixtureChannel> entry : template.getAvailableChannels().getAvailableChannels().entrySet()) {
                    if (modeString.equals(entry.getKey()) || Arrays.asList(entry.getValue().getFineChannelAliases()).contains(modeString)) {
                        // count the fine channel values for this channel in the current mode
                        int fineChannels = 0;
                        for (Object modeChannel : mode.getChannels()) {
                            if (Arrays.asList(entry.getValue().getFineChannelAliases()).contains(modeChannel)) {
                                fineChannels++;
                            }
                        }

                        channels.add(new FixtureChannelFineIndex(entry.getValue(), template, entry.getKey(), fineChannels, Arrays.asList(entry.getValue().getFineChannelAliases()).indexOf(modeString)));
                    }
                }
            } else {
                // null may be passed as a placeholder for an undefined channel
                channels.add(new FixtureChannelFineIndex(template));
            }
        }

        return channels;
    }

    private List<FixtureChannelFineIndex> getChannelsByFixture(Fixture fixture) {
        FixtureTemplate template = getTemplateByFixture(fixture);
        FixtureMode mode = getModeByFixture(fixture);

        if (mode == null) {
            return new ArrayList<>();
        }

        return getChannelsByTemplateMode(template, mode);
    }

    private Fixture getFixtureByUuid(String uuid) {
        for (Fixture fixture : project.getFixtures()) {
            if (fixture.getUuid().equals(uuid)) {
                return fixture;
            }
        }

        return null;
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<FixtureCapability> getCapabilitiesByChannel(FixtureChannel fixtureChannel) {
        List<FixtureCapability> capabilites = new ArrayList<>();

        if (fixtureChannel.getCapabilities() != null) {
            capabilites.add(fixtureChannel.getCapability());
        } else if (fixtureChannel.getCapabilities() != null) {
            capabilites.addAll(Arrays.asList(fixtureChannel.getCapabilities()));
        }

        return capabilites;
    }

    private boolean channelHasCapabilityType(FixtureChannel fixtureChannel, FixtureCapability.FixtureCapabilityType fixtureCapabilityType) {
        List<FixtureCapability> capabilites = getCapabilitiesByChannel(fixtureChannel);

        for (FixtureCapability capability : capabilites) {
            if (capability.getType().equals(fixtureCapabilityType)) {
                return true;
            }
        }

        return false;
    }

    private boolean capabilityValueMatches(FixtureCapabilityValue capabilityValue, FixtureCapability.FixtureCapabilityType capabilityType, FixtureCapability.FixtureCapabilityColor color) {
        if (capabilityValue.getType() == capabilityType
                && (color == null || capabilityValue.getColor() == color)) {
            return true;
        }
        return false;
    }

    private FixtureCapabilityValue getCapabilityValue(Preset preset, FixtureCapability.FixtureCapabilityType capabilityType, FixtureCapability.FixtureCapabilityColor color) {
        for (FixtureCapabilityValue capabilityValue : preset.getFixtureCapabilityValues()) {
            if (capabilityValueMatches(capabilityValue,
                    capabilityType,
                    color)) {
                return capabilityValue;
            }
        }
        return null;
    }

    private List<FixtureCapability> getCapabilitiesByChannelName(String channelName, String templateUuid) {
        FixtureTemplate template = this.getTemplateByUuid(templateUuid);

        for (Map.Entry<String, FixtureChannel> entry : template.getAvailableChannels().getAvailableChannels().entrySet()) {
            if (entry.getKey().equals(channelName)) {
                return this.getCapabilitiesByChannel(template.getAvailableChannels().getAvailableChannels().get(entry.getKey()));
            }
        }

        return new ArrayList<>();
    }

    private FixtureWheel getWheelByName(FixtureTemplate template, String wheelName) {
        for (Map.Entry<String, FixtureWheel> entry : template.getWheels().getWheels().entrySet()) {
            if (entry.getKey().equals(wheelName)) {
                return template.getWheels().getWheels().get(entry.getKey());
            }
        }

        // wheel not found
        return null;
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
            slots.add(wheel.getSlots()[number - 1]);
            if (wheel.getSlots()[number] != null) {
                slots.add(wheel.getSlots()[number]);
            }
        } else {
            // only one slot is set
            slots.add(wheel.getSlots()[slotNumber - 1]);
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

    private FixtureCapability getApproximatedColorWheelCapability(Preset preset, String wheelChannelName, FixtureTemplate fixtureTemplate) {
        // return an approximated wheel slot channel capability, if a color or a slot on a different
        // wheel has been selected
        Double colorRed = null;
        Double colorGreen = null;
        Double colorBlue = null;
        Double lowestDiff = Double.MAX_VALUE;
        FixtureCapability lowestDiffCapability = null;
        FixtureCapabilityValue capabilityValue;

        capabilityValue = this.getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Red);
        if (capabilityValue != null) {
            colorRed = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = this.getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Green);
        if (capabilityValue != null) {
            colorGreen = 255 * capabilityValue.getValuePercentage();
        }
        capabilityValue = this.getCapabilityValue(preset, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Blue);
        if (capabilityValue != null) {
            colorBlue = 255 * capabilityValue.getValuePercentage();
        }

        if (colorRed == null) {
            // no color found -> search the first color wheel
            // TODO
        }

        if (colorRed != null && colorGreen != null && colorBlue != null) {
            List<FixtureCapability> capabilities = getCapabilitiesByChannelName(wheelChannelName, fixtureTemplate.getUuid());

            for (FixtureCapability capability : capabilities) {
                if (capability.getSlotNumber() != null) {
                    FixtureWheel wheel = getWheelByName(fixtureTemplate, (capability.getWheel() != null) ? capability.getWheel() : wheelChannelName);
                    Color mixedColor = getMixedWheelSlotColor(wheel, capability.getSlotNumber());
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

    // return all fixture uuids with their corresponding channel values
    private Map<String, List<FixtureChannelValue>> getChannelValues(long timeMillis, List<PresetRegionScene> presets) {
        // Loop over all relevant presets and calc the property values from the presets (capabilities and effects)
        HashMap<String, List<FixtureChannelValue>> calculatedFixtures = new HashMap<>();

        if (project == null) {
            return calculatedFixtures;
        }

        for (int i = 0; i < project.getFixtures().length; i++) {
            Fixture fixture = project.getFixtures()[i];

            // all values of the current fixture channels
            List<FixtureChannelValue> values = new ArrayList<>();

            Fixture alreadyCalculatedFixture = getAlreadyCalculatedFixture(project.getFixtures(), i);

            if (alreadyCalculatedFixture == null) {
                List<FixtureChannelFineIndex> channelFineIndices = getChannelsByFixture(fixture);

                // apply the default channels
                for (FixtureChannelFineIndex channelFineIndex : channelFineIndices) {
                    FixtureChannel channel = channelFineIndex.getFixtureChannel();

                    if (channel != null && channel.getDefaultValue() != null) {
                        Double defaultValue = getDefaultValueByChannel(channel);

                        if (defaultValue != null) {
                            this.mixChannelValue(values, new FixtureChannelValue(channelFineIndex.getChannelName(), channelFineIndex.getFixtureTemplate().getUuid(), defaultValue), 1);
                        }
                    }
                }

                for (PresetRegionScene preset : presets) {
                    // When fading is in progress (on preset or scene-level), the current preset does not
                    // fully cover underlying values.
                    // -> 0 = no covering at all, 1 = fully cover (no fading)
                    double intensityPercentageScene = 1;
                    double intensityPercentagePreset = 1;
                    double intensityPercentage = 1;

                    // Fade out is stronger than fade in (if they overlap)

                    if (preset.getRegion() != null && preset.getScene() != null) {
                        // Take away intensity for scene fading
                        if (timeMillis > preset.getRegion().getEndMillis() - preset.getScene().getFadeOutMillis()) {
                            // Scene fades out
                            intensityPercentageScene = (preset.getRegion().getEndMillis() - timeMillis) / (double) preset.getScene().getFadeOutMillis();
                        } else if (timeMillis < preset.getRegion().getStartMillis() + preset.getScene().getFadeInMillis()) {
                            // Scene fades in
                            intensityPercentageScene = (timeMillis - preset.getRegion().getStartMillis()) / (double) preset.getScene().getFadeInMillis();
                        }
                    }

                    if (preset.getRegion() != null && preset.getPreset() != null) {
                        // Take away intensity for preset fading
                        if (preset.getPreset().getEndMillis() != null && timeMillis > preset.getRegion().getStartMillis() + preset.getPreset().getEndMillis() - preset.getPreset().getFadeOutMillis()) {
                            // Preset fades out
                            intensityPercentagePreset = (preset.getRegion().getStartMillis() + preset.getPreset().getEndMillis() - timeMillis) / (double) preset.getPreset().getFadeOutMillis();
                        }

                        if (preset.getPreset().getStartMillis() != null && timeMillis < preset.getRegion().getStartMillis() + preset.getPreset().getStartMillis() + preset.getPreset().getFadeInMillis()) {
                            // Preset fades in
                            intensityPercentagePreset = (timeMillis - preset.getRegion().getStartMillis() + preset.getPreset().getStartMillis()) / (double) preset.getPreset().getFadeInMillis();
                        }

                        // If the preset and the scene, both are fading, take the stronger
                        intensityPercentage = Math.min(intensityPercentageScene, intensityPercentagePreset);
                    }

                    // Search for this fixture in the preset and get it's preset-specific index (for chasing effects)
                    Integer fixtureIndex = getFixtureIndex(preset.getPreset(), fixture.getUuid());
                    boolean hasColor = false;

                    if (fixtureIndex != null && fixtureIndex >= 0) {
                        // this fixture is also in the preset
                        FixtureTemplate template = getTemplateByFixture(fixture);

                        // mix the preset capability values
                        for (FixtureChannelFineIndex channelFineIndex : channelFineIndices) {
                            FixtureChannel channel = channelFineIndex.getFixtureChannel();

                            if (channel != null) {
                                List<FixtureCapability> channelCapabilities = getCapabilitiesByChannel(channel);

                                for (FixtureCapabilityValue presetCapabilityValue : preset.getPreset().getFixtureCapabilityValues()) {
                                    for (FixtureCapability channelCapability : channelCapabilities) {
                                        String wheelName = (channelCapability.getWheel() != null) ? channelCapability.getWheel() : channelFineIndex.getChannelName();

                                        if (presetCapabilityValue.getType() == channelCapability.getType()
                                                && (presetCapabilityValue.getColor() == null || presetCapabilityValue.getColor() == channelCapability.getColor())
                                                && (presetCapabilityValue.getWheel() == null || (presetCapabilityValue.getWheel().equals(wheelName) && presetCapabilityValue.getFixtureTemplateUuid().equals(template.getUuid())))) {

                                            // the capabilities match -> apply the value, if possible
                                            if (presetCapabilityValue.getValuePercentage() != null && (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.Intensity ||
                                                    presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity)) {

                                                Double valuePercentage = presetCapabilityValue.getValuePercentage();

                                                // brightness property
                                                if (channelCapabilities.size() == 1) {
                                                    // the only capability in this channel
                                                    FixtureChannelValue channelValue = new FixtureChannelValue(channelFineIndex.getChannelName(), template.getUuid(), getMaxValueByChannel(channel) * valuePercentage);
                                                    this.mixChannelValue(values, channelValue, intensityPercentage);

                                                    if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                                        hasColor = true;
                                                    }
                                                } else {
                                                    // more than one capability in the channel
                                                    if ("off".equals(channelCapability.getBrightness()) && valuePercentage == 0) {
                                                        FixtureChannelValue channelValue = new FixtureChannelValue(channelFineIndex.getChannelName(), template.getUuid(), (double)(channelCapability.getDmxRange()[0]));
                                                        this.mixChannelValue(values, channelValue, intensityPercentage);

                                                        if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                                            hasColor = true;
                                                        }
                                                    } else if (("dark".equals(channelCapability.getBrightnessStart()) || "off".equals(channelCapability.getBrightnessStart())) && "bright".equals(channelCapability.getBrightnessEnd())) {
                                                        double value = (channelCapability.getDmxRange()[1] - channelCapability.getDmxRange()[0]) * valuePercentage + channelCapability.getDmxRange()[0];
                                                        FixtureChannelValue channelValue = new FixtureChannelValue(channelFineIndex.getChannelName(), template.getUuid(), value);
                                                        this.mixChannelValue(values, channelValue, intensityPercentage);

                                                        if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity) {
                                                            hasColor = true;
                                                        }
                                                    }
                                                }
                                            } else if (presetCapabilityValue.getType() == FixtureCapability.FixtureCapabilityType.WheelSlot
                                                    && channelCapability.getSlotNumber().equals(presetCapabilityValue.slotNumber)) {

                                                // wheel slot (color, gobo, etc.)
                                                FixtureChannelValue channelValue = new FixtureChannelValue(channelFineIndex.getChannelName(), template.getUuid(), Math.floor(((double) channelCapability.getDmxRange()[0] + (double) channelCapability.getDmxRange()[1]) / 2));
                                                this.mixChannelValue(values, channelValue, 1);

                                                // check, whether we just set a color wheel value
                                                FixtureWheel wheel = getWheelByName(template, (channelCapability.getWheel() != null) ? channelCapability.getWheel() : channelFineIndex.getChannelName());
                                                List<FixtureWheelSlot> wheelSlots = getWheelSlots(wheel, channelCapability.getSlotNumber());
                                                for (FixtureWheelSlot slot : wheelSlots) {
                                                    if (slot.getColors().size() > 0) {
                                                        hasColor = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // approximate the color from a color or a different color wheel, if necessary
                                if (!hasColor) {
                                    // search for a color wheel on this fixture
                                    FixtureWheel colorWheel = null;
                                    for (FixtureCapability channelCapability : channelCapabilities) {
                                        if (channelCapability.getType() == FixtureCapability.FixtureCapabilityType.WheelSlot) {
                                            FixtureWheel wheel = getWheelByName(template, (channelCapability.getWheel() != null) ? channelCapability.getWheel() : channelFineIndex.getChannelName());
                                            List<FixtureWheelSlot> wheelSlots = getWheelSlots(wheel, channelCapability.getSlotNumber());
                                            for (FixtureWheelSlot slot : wheelSlots) {
                                                if (slot.getColors().size() > 0) {
                                                    colorWheel = wheel;
                                                }
                                            }
                                        }
                                    }

                                    if (colorWheel != null) {
                                        // there is a color wheel
                                        FixtureCapability capability = getApproximatedColorWheelCapability(preset.getPreset(), channelFineIndex.getChannelName(), template);

                                        if (capability != null) {
                                            // we found an approximated color in the available wheel channel
                                            FixtureChannelValue channelValue = new FixtureChannelValue(channelFineIndex.getChannelName(), template.getUuid(), Math.floor((capability.getDmxRange()[0] + capability.getDmxRange()[1]) / 2));
                                            this.mixChannelValue(values, channelValue, 1);
                                        }
                                    }
                                }
                            }
                        }

                        // mix the preset channel values (overwrite the capabilities, if necessary)
                        for (FixtureChannelFineIndex channelFineIndex : channelFineIndices) {
                            FixtureChannel channel = channelFineIndex.getFixtureChannel();

                            if (channel != null) {
                                for (FixtureChannelValue channelValue : preset.getPreset().getFixtureChannelValues()) {
                                    if (template.getUuid().equals(channelValue.getFixtureTemplateUuid()) && channelFineIndex.getChannelName() == channelValue.getChannelName()) {
                                        this.mixChannelValue(values, channelValue, intensityPercentage);
                                    }
                                }
                            }
                        }

                        // TODO
                        // Match all effect capabilities of this preset with the fixture capabilities
//                        for (Effect effect : preset.getPreset().getEffects()) {
//                            List<FixtureChannelValue> effectChannelValues = new ArrayList<>();
//                            double value = effect.getValueAtMillis(timeMillis, fixtureIndex);
//
//                            for (Effect.EffectChannel effectChannel : effect.getEffectChannels()) {
//                                switch (effectChannel) {
//                                    case dimmer:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.Intensity));
//                                        break;
//                                    case pan:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.Pan));
//                                        break;
//                                    case tilt:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.Tilt));
//                                        break;
//                                    case colorRed:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Red));
//                                        break;
//                                    case colorGreen:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Green));
//                                        break;
//                                    case colorBlue:
//                                        effectChannelValues.add(new FixtureCapabilityValue(value, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Blue));
//                                        break;
//                                }
//                            }
//
//                            for (FixtureChannelFineIndex channelFineIndex : channelFineIndices) {
//                                FixtureChannel channel = channelFineIndex.getFixtureChannel();
//
//                                if (channel != null) {
//                                    for (FixtureChannelValue effectChannelValue : effectChannelValues) {
//                                        if (channel.getCapability().getType() == effectChannelValue.getType()) {
//                                            this.mixChannelValue(values, effectChannelValue, intensityPercentage);
//                                        }
//                                    }
//                                }
//                            }
//                        }
                    }
                }
            }

            // Store the calculated values for subsequent fixtures on the same DMX address
            calculatedFixtures.put(fixture.getUuid(), values);
        }

        return calculatedFixtures;
    }

    private int getDmxValue(double value, int fineValueCount, int fineIndex) {
        // return the rounded dmx value in the specified fineness
        if (fineIndex >= 0) {
            // a finer value is requested. calculate it by substracting the value
            // which has been returned on the current level.
            return this.getDmxValue((value - Math.floor(value)) * 255, fineValueCount - 1, fineIndex - 1);
        } else {
            // we reached the required fineness of the value
            if (fineValueCount > fineIndex + 1) {
                // there are finer values still available -> floor the current value
                return (int) Math.floor(value);
            } else {
                // there are no finer values available -> round it
                return (int) Math.round(value);
            }
        }
    }

    private void setUniverseValues(Map<String, List<FixtureChannelValue>> fixtures, double masterDimmerValue) {
        // Reset all DMX universes
        for (LightingUniverse universe : lightingUniverses) {
            universe.reset();
        }

        for (Map.Entry<String, List<FixtureChannelValue>> entry : fixtures.entrySet()) {
            String fixtureUuid = entry.getKey();
            List<FixtureChannelValue> channelValues = entry.getValue();

            Fixture fixture = getFixtureByUuid(fixtureUuid);

            // TODO Get the correct universe for this fixture
            //let universe: Universe = this.universeService.getUniverseByUuid(fixture.dmxUniverseUuid);
            LightingUniverse universe = lightingUniverses.get(0);

            FixtureTemplate template = getTemplateByUuid(fixture.getFixtureTemplateUuid());
            List<FixtureChannelFineIndex> channelFineIndices = getChannelsByFixture(fixture);

            for (int channelIndex = 0; channelIndex < channelFineIndices.size(); channelIndex++) {
                FixtureChannel channel = channelFineIndices.get(channelIndex).getFixtureChannel();

                // TODO
//                for (FixtureChannelValue channelValue : channelValues) {
//                    if (channel != null && channel.getCapability().getType() == capability.getType()) {
//                        int universeChannel = fixture.getDmxFirstChannel() + channelIndex;
//                        int value = getDmxValue(capability.getValue(), channelFineIndices.get(channelIndex).getFineValueCount(), channelFineIndices.get(channelIndex).getFineIndex());
//
//                        universe.getUniverse().put(universeChannel, value);
//
//                        // TODO Set the fine properties, if available
//
//                        // TODO apply the master dimmer value to dimmer channels
//                    }
//                }
            }
        }
    }

    private void calculateUniverse(long timeMillis) {
        List<PresetRegionScene> presets = getPresets(timeMillis);
        Map<String, List<FixtureChannelValue>> calculatedFixtures = getChannelValues(timeMillis, presets);
        // TODO make the dimmer value adjustable and fall back to the project settings
        setUniverseValues(calculatedFixtures, 1);

        logger.info(lightingUniverses.get(0).getUniverse().toString());
    }

    private void startTimer() {
        if (sendUniverseTimer != null || settingsService.getSettings().getDesignerFrequencyHertz() == null) {
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
                // Calculate and send the current state
                calculateUniverse(getCurrentPositionMillis());
                lightingService.sendExternalSync();
//                }
            }
        };

        sendUniverseTimer = new Timer();
        sendUniverseTimer.schedule(timerTask, 0, 1000 / settingsService.getSettings().getDesignerFrequencyHertz());
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
        stopTimer();

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            lightingService.removeLightingUniverse(lightingUniverse);
        }

        project = null;
        pipeline = null;
    }

    @Override
    public long getPositionMillis() {
        return getCurrentPositionMillis();
    }

}
