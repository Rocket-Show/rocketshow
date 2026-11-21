package com.ascargon.rocketshow.lighting.designer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Migrates a project of an older designer to the version the shared lighting engine reads (its compiler reads the
 * current version only, see docs/lighting-engine.md), like the designer migrates it when it opens it. The conformance
 * cases of the designer of the kind "migration" check that both come to the same project.
 *
 * @author Moritz A. Vieli
 */
public final class ProjectMigration {

    private final static Logger logger = LoggerFactory.getLogger(ProjectMigration.class);

    // the highest project version this player knows. A project written by a newer designer still plays as well as it
    // can, but whatever came after this version is missing from it.
    public static final int SUPPORTED_PROJECT_VERSION = 11;

    // A pixel of a fixture matrix at its position, counted from 1 like in the fixture library (a pixel group has none)
    record MatrixPixel(String key, int x, int y, int z) {
    }

    private ProjectMigration() {
    }

    /**
     * Migrate the passed project to the current version, in place. A project of the current version stays as it is.
     */
    public static void migrate(Project project) {
        if (project == null) {
            return;
        }

        if (project.getVersion() > SUPPORTED_PROJECT_VERSION) {
            logger.warn("Designer project '" + project.getName() + "' was written with a newer designer (project version " + project.getVersion() + ", this Rocket Show knows version " + SUPPORTED_PROJECT_VERSION + "). Parts of the show may be missing. Please update Rocket Show.");
        }

        warnAboutUnknownEffects(project);

        migrateToVersion2(project);
        migrateToVersion3(project);
        migrateToVersion7(project);
        migrateToVersion8(project);
        migrateToVersion9(project);
        migrateToVersion10(project);
        migrateToVersion11(project);
    }

    // An effect of a type a newer designer added is left out (see EffectUnknown), the rest of the show still plays.
    // Reported once for each of these types.
    private static void warnAboutUnknownEffects(Project project) {
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
    private static void migrateToVersion2(Project project) {
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
    private static List<PresetFixture> getPresetFixturesOfVersion1(Project project, String fixtureUuid) {
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

        for (MatrixPixel pixel : fixtureGetUniquePixels(profile, fixture)) {
            PresetFixture presetFixture = new PresetFixture();
            presetFixture.setFixtureUuid(fixtureUuid);
            presetFixture.setPixelKey(pixel.key());
            presetFixtures.add(presetFixture);
        }

        return presetFixtures;
    }

    private static void migrateToVersion3(Project project) {
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

    private static void migrateToVersion7(Project project) {
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

    private static void migrateToVersion8(Project project) {
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

    private static void migrateToVersion9(Project project) {
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
    private static void migrateToVersion10(Project project) {
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

    // Version 11 brought the pixel effects and the dimmers of the fixtures lighting in colors at full (see
    // Project.colorDimmersAtFull). An older project keeps them off, so it plays as it did, like in the designer.
    private static void migrateToVersion11(Project project) {
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

    private static int getGlobalFixtureIndex(Project project, PresetFixture presetFixture) {
        for (int i = 0; i < project.getPresetFixtures().size(); i++) {
            PresetFixture projectFixture = project.getPresetFixtures().get(i);

            if (projectFixture.getFixtureUuid().equals(presetFixture.getFixtureUuid())
                    && Objects.equals(emptyToNull(projectFixture.getPixelKey()), emptyToNull(presetFixture.getPixelKey()))) {
                return i;
            }
        }

        return -1;
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    // ---- the fixtures of a project of version 1 ----

    private static Fixture getFixtureByUuid(Project project, String uuid) {
        for (Fixture fixture : project.getFixtures()) {
            if (Objects.equals(fixture.getUuid(), uuid)) {
                return fixture;
            }
        }

        return null;
    }

    private static FixtureProfile getProfileByUuid(Project project, String uuid) {
        if (project.getFixtureProfiles() == null) {
            return null;
        }

        for (FixtureProfile fixtureProfile : project.getFixtureProfiles()) {
            if (fixtureProfile.getUuid().equals(uuid)) {
                return fixtureProfile;
            }
        }

        return null;
    }

    private static FixtureMode getModeByFixture(FixtureProfile profile, Fixture fixture) {
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

    // the pixels the channels of the mode of the fixture are repeated for, each once
    private static List<MatrixPixel> fixtureGetUniquePixels(FixtureProfile profile, Fixture fixture) {
        List<MatrixPixel> pixels = new ArrayList<>();

        for (FixtureModeChannel channel : getModeByFixture(profile, fixture).getChannels()) {
            for (MatrixPixel pixel : getPixelsInOrder(profile, channel.getRepeatFor())) {
                if (pixels.stream().noneMatch(known -> known.key().equals(pixel.key()))) {
                    pixels.add(pixel);
                }
            }
        }

        return pixels;
    }

    // does the fixture have a channel of its own in its mode, not one of its pixels?
    private static boolean fixtureHasGeneralChannel(FixtureProfile profile, Fixture fixture) {
        for (FixtureModeChannel channel : getModeByFixture(profile, fixture).getChannels()) {
            if (getPixelsInOrder(profile, channel.getRepeatFor()).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    // ---- the pixels of a fixture matrix ----

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

    // All pixels of the matrix at their positions, counted from 1 like in the fixture library. The library gives the
    // pixel keys by Z, Y and X, so the pixels come layer by layer and row by row: X changes fastest. Package visible
    // for the tests.
    static List<MatrixPixel> getAllPixels(FixtureProfile profile) {
        List<MatrixPixel> result = new ArrayList<>();
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
                            result.add(new MatrixPixel(row.get(x), x + 1, y + 1, z + 1));
                        }
                    }
                }
            }
        } else if (matrix.getPixelCount() != null && matrix.getPixelCount().size() == 3) {
            List<Integer> pixelCount = matrix.getPixelCount();

            for (int z = 1; z <= pixelCount.get(2); z++) {
                for (int y = 1; y <= pixelCount.get(1); y++) {
                    for (int x = 1; x <= pixelCount.get(0); x++) {
                        result.add(new MatrixPixel(getDefaultPixelKey(pixelCount, x, y, z), x, y, z));
                    }
                }
            }
        }

        return result;
    }

    private static int getPosition(MatrixPixel pixel, char axis) {
        return switch (axis) {
            case 'X' -> pixel.x();
            case 'Y' -> pixel.y();
            default -> pixel.z();
        };
    }

    // The pixels in the order of the passed axes, e.g. "YXZ": Y changes fastest, Z slowest
    private static List<MatrixPixel> getPixelsByAxes(List<MatrixPixel> pixels, String axes) {
        List<MatrixPixel> result = new ArrayList<>(pixels);
        result.sort(Comparator.<MatrixPixel>comparingInt(pixel -> getPosition(pixel, axes.charAt(2)))
                .thenComparingInt(pixel -> getPosition(pixel, axes.charAt(1)))
                .thenComparingInt(pixel -> getPosition(pixel, axes.charAt(0))));
        return result;
    }

    private static List<String> getPixelGroupNames(FixtureProfile profile) {
        if (profile.getMatrix() == null || profile.getMatrix().getPixelGroups() == null) {
            return List.of();
        }

        return profile.getMatrix().getPixelGroups().stream().map(FixtureMatrixPixelGroup::getName).toList();
    }

    // The pixels a channel of the mode is repeated for, in the order of their channels. A pixel group counts as one:
    // its channels (e.g. "Red Inner ring") control all of its pixels at once. Package visible for the tests.
    static List<MatrixPixel> getPixelsInOrder(FixtureProfile profile, List<String> repeatFor) {
        List<MatrixPixel> result = new ArrayList<>();

        if (repeatFor == null || repeatFor.isEmpty() || profile.getMatrix() == null) {
            // a channel of its own, no pixels
            return result;
        }

        List<MatrixPixel> allPixels = getAllPixels(profile);
        List<String> groupNames = getPixelGroupNames(profile);

        if (repeatFor.size() == 1 && repeatFor.getFirst().startsWith("eachPixel")) {
            String order = repeatFor.getFirst().substring("eachPixel".length());

            if (order.equals("ABC")) {
                // the pixel keys in alphanumerical order
                result.addAll(allPixels);
                result.sort((pixel1, pixel2) -> compareKeys(pixel1.key(), pixel2.key()));
            } else if (order.equals("Group")) {
                // the pixel groups in the order of the profile
                for (String groupName : groupNames) {
                    result.add(new MatrixPixel(groupName, 0, 0, 0));
                }
            } else if (order.length() == 3 && order.contains("X") && order.contains("Y") && order.contains("Z")) {
                // by position, e.g. eachPixelXYZ
                result.addAll(getPixelsByAxes(allPixels, order));
            }

            return result;
        }

        // pixel keys and pixel groups
        for (String entry : repeatFor) {
            MatrixPixel pixel = allPixels.stream().filter(candidate -> candidate.key().equals(entry)).findFirst().orElse(null);

            if (pixel != null) {
                result.add(pixel);
            } else if (groupNames.contains(entry)) {
                result.add(new MatrixPixel(entry, 0, 0, 0));
            }
        }

        return result;
    }

    // the digits 0 to 9 only, like the designer (Character.isDigit would also take the digits of other scripts)
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    // Pixel keys like the fixture library sorts them: numbers by their value (2 before 10), the rest alphabetically
    static int compareKeys(String key1, String key2) {
        int index1 = 0;
        int index2 = 0;

        while (index1 < key1.length() && index2 < key2.length()) {
            char char1 = key1.charAt(index1);
            char char2 = key2.charAt(index2);

            if (isDigit(char1) && isDigit(char2)) {
                int start1 = index1;
                int start2 = index2;
                while (index1 < key1.length() && isDigit(key1.charAt(index1))) index1++;
                while (index2 < key2.length() && isDigit(key2.charAt(index2))) index2++;

                int result = new BigInteger(key1.substring(start1, index1)).compareTo(new BigInteger(key2.substring(start2, index2)));
                if (result != 0) return result;
            } else {
                int result = Character.compare(Character.toLowerCase(char1), Character.toLowerCase(char2));
                if (result != 0) return result;
                index1++;
                index2++;
            }
        }

        return Integer.compare(key1.length() - index1, key2.length() - index2);
    }

}
