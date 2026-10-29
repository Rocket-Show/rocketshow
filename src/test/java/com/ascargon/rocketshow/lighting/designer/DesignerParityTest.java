package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine reads and works out a project like the designer does, where the two could differ: numbers which are
 * null, missing or not whole, the default values, colors and pixel groups of the fixture library. The specs of the
 * designer check the same cases (see "parity" in the specs of the designer).
 */
class DesignerParityTest {

    private static <T> T read(String json, Class<T> type) {
        return DesignerJson.MAPPER.readValue(json, type);
    }

    @Test
    void keepsTheDefaultOfAValueWhichIsNull() {
        Scene scene = read("{\"dimmer\": null, \"fadeInMillis\": null, \"fadeOutMillis\": null}", Scene.class);
        assertEquals(1, scene.getDimmer());
        assertEquals(0, scene.getFadeInMillis());
        assertEquals(0, scene.getFadeOutMillis());

        EffectCurve curve = read("{\"type\": \"curve\", \"lengthMillis\": null, \"amplitude\": null, \"position\": null, \"curveType\": null}", EffectCurve.class);
        assertEquals(2500, curve.getLengthMillis());
        assertEquals(1, curve.getAmplitude());
        assertEquals(0.5, curve.getPosition());
        assertEquals("sine", curve.getCurveType());
        assertTrue(curve.isVisible());

        Project project = read("{\"masterDimmerValue\": null}", Project.class);
        assertEquals(1, project.getMasterDimmerValue());
    }

    @Test
    void startsASceneWithoutFades() {
        Scene scene = read("{}", Scene.class);
        assertEquals(0, scene.getFadeInMillis());
        assertEquals(0, scene.getFadeOutMillis());
    }

    @Test
    void readsANameItDoesNotKnowAsMissing() {
        FixtureCapability capability = read("{\"type\": \"SomethingNew\", \"color\": \"Ultraviolet\"}", FixtureCapability.class);
        assertNull(capability.getType());
        assertNull(capability.getColor());
    }

    @Test
    void keepsTheFractionsOfTheNumbers() {
        EffectCurve curve = read("{\"type\": \"curve\", \"lengthMillis\": 1000.5, \"amplitude\": 0.1, \"dutyCycle\": 0.3, \"phasingMillis\": 0.25}", EffectCurve.class);
        assertEquals(1000.5, curve.getLengthMillis());
        // as a double, not a float
        assertEquals(0.1, curve.getAmplitude());
        assertEquals(0.3, curve.getDutyCycle());
        assertEquals(0.25, curve.getPhasingMillis(1, 2, null));

        Preset preset = read("{\"fadeInMillis\": 1000.5, \"startMillis\": 250.25, \"stepsPhasingCycles\": 0.1}", Preset.class);
        assertEquals(1000.5, preset.getFadeInMillis());
        assertEquals(250.25, preset.getStartMillis());
        assertEquals(0.1, preset.getStepsPhasingCycles());

        ScenePlaybackRegion region = read("{\"startMillis\": 1234.7, \"endMillis\": 6857.142857}", ScenePlaybackRegion.class);
        assertEquals(1234.7, region.getStartMillis());
        assertEquals(6857.142857, region.getEndMillis());
    }

    @Test
    void roundsTheNumbersOfFixturesAndCyclesLikeTheDesigner() {
        // a group of 1.5 fixtures is one of 2 (it was one of 1 before)
        EffectCurve curve = read("{\"type\": \"curve\", \"phasingMillis\": 100, \"phasingGroupSize\": 1.5, \"runMode\": \"cycles\", \"runCycles\": 1.5, \"lengthMillis\": 1000}", EffectCurve.class);
        assertEquals(0, curve.getPhasingMillis(1, 4, null));
        assertEquals(100, curve.getPhasingMillis(2, 4, null));
        assertEquals(2000, curve.getRunEndMillis(null));

        Preset preset = read("{\"stepsPhasingMillis\": 100, \"stepsPhasingGroupSize\": 1.5}", Preset.class);
        assertEquals(0, PresetSteps.getStepsPhasingMillis(preset, 1, 4));
        assertEquals(100, PresetSteps.getStepsPhasingMillis(preset, 2, 4));

        // 2.5 rounds up, like Math.round of the browser
        preset.setStepsPhasingGroupSize(2.5);
        assertEquals(0, PresetSteps.getStepsPhasingMillis(preset, 2, 4));
        assertEquals(100, PresetSteps.getStepsPhasingMillis(preset, 3, 4));
    }

    @Test
    void fadesOverTheFractionsOfAMillisecond() {
        Scene scene = read("{\"fadeInMillis\": 1000}", Scene.class);
        ScenePlaybackRegion region = read("{\"startMillis\": 0.5, \"endMillis\": 10000}", ScenePlaybackRegion.class);
        Preset preset = new Preset();

        assertEquals(0.5, DefaultDesignerService.getPresetIntensity(new PresetRegionScene(preset, region, scene), 500.5));
    }

    @Test
    void readsTheNumbersOfTheFixtureLibraryLikeTheDesigner() {
        assertEquals(12.5, DefaultDesignerService.parseNumber("12.5"));
        assertEquals(12.5, DefaultDesignerService.parseNumber(" 12.5 "));
        assertEquals(-3d, DefaultDesignerService.parseNumber("-3"));
        assertEquals(0.5, DefaultDesignerService.parseNumber(".5"));
        assertEquals(5d, DefaultDesignerService.parseNumber("5."));
        assertEquals(1000d, DefaultDesignerService.parseNumber("1e3"));

        // neither Double.parseDouble only nor Number() only
        assertNull(DefaultDesignerService.parseNumber("1d"));
        assertNull(DefaultDesignerService.parseNumber("NaN"));
        assertNull(DefaultDesignerService.parseNumber("Infinity"));
        assertNull(DefaultDesignerService.parseNumber("0x10"));
        assertNull(DefaultDesignerService.parseNumber(""));
        assertNull(DefaultDesignerService.parseNumber("bright"));
    }

    private static FixtureChannel channel(String json) {
        return read(json, FixtureChannel.class);
    }

    @Test
    void readsTheDefaultValuesLikeTheDesigner() {
        // a fraction of a percent on a 16 bit channel (the designer took 12 % before)
        assertEquals(65535 * 0.125, DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": \"12.5%\", \"fineChannelAliases\": [\"Tilt fine\"]}")));
        assertEquals(255 * 0.333, DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": \"33.3%\"}")), 1e-9);
        assertEquals(128.5, DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": 128.5}")));

        // a default of 0 is one (the designer left it out before)
        assertEquals(0, DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": 0}")));
        assertEquals(0, DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": \"0%\"}")));

        // none, and one which can't be read (which stopped the whole project before)
        assertNull(DefaultDesignerService.getDefaultValueByChannel(channel("{}")));
        assertNull(DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": \"high\"}")));
        assertNull(DefaultDesignerService.getDefaultValueByChannel(channel("{\"defaultValue\": \"high%\"}")));
    }

    @Test
    void readsTheColorsLikeTheDesigner() {
        Color orange = DefaultDesignerService.hexToRgb("#ff8000");
        assertEquals(List.of(255d, 128d, 0d), List.of(orange.getRed(), orange.getGreen(), orange.getBlue()));

        Color shortOrange = DefaultDesignerService.hexToRgb("#f80");
        assertEquals(List.of(255d, 136d, 0d), List.of(shortOrange.getRed(), shortOrange.getGreen(), shortOrange.getBlue()));

        Color withoutHash = DefaultDesignerService.hexToRgb("FF8000");
        assertEquals(List.of(255d, 128d, 0d), List.of(withoutHash.getRed(), withoutHash.getGreen(), withoutHash.getBlue()));

        assertNull(DefaultDesignerService.hexToRgb("orange"));
        assertNull(DefaultDesignerService.hexToRgb("#ff80"));
        assertNull(DefaultDesignerService.hexToRgb(null));
    }

    @Test
    void leavesTheColorsOutWhichItCannotReadWhenItMixesThem() {
        Color mixed = DefaultDesignerService.mixHexColors(List.of("#ff0000", "orange", "#0000ff"));
        assertEquals(List.of(127.5, 0d, 127.5), List.of(mixed.getRed(), mixed.getGreen(), mixed.getBlue()));

        assertNull(DefaultDesignerService.mixHexColors(List.of("orange")));
    }

    private static List<FixtureMatrixPixelGroup> pixelGroups(String pixelGroups) {
        return read("{\"pixelCount\": [3, 1, 1], \"pixelGroups\": " + pixelGroups + "}", FixtureMatrix.class).getPixelGroups();
    }

    @Test
    void takesThePixelGroupsInTheOrderOfTheBrowser() {
        // the names of array indices come first and by their number, like Object.keys gives them in the designer
        List<String> names = pixelGroups("{\"Master\": \"all\", \"2\": [\"2\"], \"10\": [\"3\"], \"1\": [\"1\"], \"01\": [\"1\"], \"Inner\": [\"2\"]}")
                .stream().map(FixtureMatrixPixelGroup::getName).toList();

        assertEquals(List.of("1", "2", "10", "Master", "01", "Inner"), names);
    }

    @Test
    void readsThePixelGroupsOfEarlierDesigners() {
        List<FixtureMatrixPixelGroup> groups = pixelGroups("""
                {
                  "wrapped all": {"isAll": "all", "constraints": null},
                  "wrapped keys": {"isAll": false, "constraints": ["1", "2"]},
                  "wrapped twice": {"isAll": {"isAll": false, "constraints": {"x": ["<=2"]}}, "constraints": null},
                  "all flag": {"isAll": true, "constraints": null},
                  "keys in an object": {"x": [], "keys": ["3"]},
                  "nothing": {"isAll": false, "constraints": null}
                }
                """);

        assertTrue(groups.get(0).isAll());
        assertEquals(List.of("1", "2"), groups.get(1).getConstraints().getKeys());
        assertFalse(groups.get(2).isAll());
        assertEquals(List.of("<=2"), groups.get(2).getConstraints().getX());
        assertTrue(groups.get(3).isAll());
        assertEquals(List.of("3"), groups.get(4).getConstraints().getKeys());

        // a group without a definition has no pixels (it had all of them before)
        assertFalse(groups.get(5).isAll());
        assertNull(groups.get(5).getConstraints());
        assertEquals(List.of(), new DefaultFixturePixelGroupResolveService().resolveGroupPixels(groups.get(5), List.of(new CachedFixturePixel())));
    }

    private static CachedFixturePixel pixel(String key, int x) {
        CachedFixturePixel pixel = new CachedFixturePixel();
        pixel.setKey(key);
        pixel.setX(x);
        return pixel;
    }

    @Test
    void resolvesThePixelGroupsLikeTheDesigner() {
        DefaultFixturePixelGroupResolveService service = new DefaultFixturePixelGroupResolveService();
        List<CachedFixturePixel> pixels = List.of(pixel("b", 1), pixel("a", 2), pixel("c", 3));

        // a plain number is written without a sign
        FixtureMatrixPixelGroup plus = pixelGroups("{\"group\": {\"x\": [\"+2\"]}}").getFirst();
        assertThrows(IllegalArgumentException.class, () -> service.resolveGroupPixels(plus, pixels));

        // constraints by position keep the order of the pixels, only a name sorts them
        FixtureMatrixPixelGroup positions = pixelGroups("{\"group\": {\"x\": [\">=1\"], \"name\": []}}").getFirst();
        assertEquals(List.of("b", "a", "c"), service.resolveGroupPixels(positions, pixels).stream().map(CachedFixturePixel::getKey).toList());

        // only the digits 0 to 9 are numbers
        assertTrue(DefaultFixturePixelGroupResolveService.compareKeys("2", "10") < 0);
        assertTrue(DefaultFixturePixelGroupResolveService.compareKeys("٢", "10") > 0);
    }

}
