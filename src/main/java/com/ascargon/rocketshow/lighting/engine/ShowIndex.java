package com.ascargon.rocketshow.lighting.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Which uuid of a project has which number in the show compiled from it: the fixtures, the presets (with the uuids of
 * their steps), the scenes, the compositions (with their names) and the universes, each at its number, and the parts
 * of the fixtures with the names of their channels. A uuid a project has twice has two numbers.
 */
public record ShowIndex(List<String> fixtures, List<Part> parts, List<String> presets, List<List<String>> steps,
                        List<String> scenes, List<CompositionName> compositions, List<String> universes) {

    // a fixture, or one of its pixels, with the names of its channels in the order of the values of the engine
    public record Part(int fixture, String pixelKey, List<String> channels) {
    }

    public record CompositionName(String uuid, String name) {
    }

    public ShowIndex {
        fixtures = fixtures == null ? List.of() : fixtures;
        parts = parts == null ? List.of() : parts;
        presets = presets == null ? List.of() : presets;
        steps = steps == null ? List.of() : steps;
        scenes = scenes == null ? List.of() : scenes;
        compositions = compositions == null ? List.of() : compositions;
        universes = universes == null ? List.of() : universes;
    }

    // the numbers of the fixtures with the passed uuid (none, one or, for a uuid used twice, several)
    public List<Integer> fixtureNumbers(String uuid) {
        List<Integer> numbers = new ArrayList<>();

        for (int number = 0; number < fixtures.size(); number++) {
            if (fixtures.get(number).equals(uuid)) {
                numbers.add(number);
            }
        }

        return numbers;
    }

    // the number of the preset with the passed uuid, or -1
    public int preset(String uuid) {
        return uuid == null ? -1 : presets.indexOf(uuid);
    }

    // the number of a step within its preset, or -1
    public int step(int preset, String uuid) {
        if (preset < 0 || preset >= steps.size() || uuid == null) {
            return -1;
        }

        return steps.get(preset).indexOf(uuid);
    }

    // the number of the scene with the passed uuid, or -1
    public int scene(String uuid) {
        return uuid == null ? -1 : scenes.indexOf(uuid);
    }

    // the number of the first composition with the passed name, or -1
    public int compositionNamed(String name) {
        for (int number = 0; number < compositions.size(); number++) {
            if (compositions.get(number).name() != null && compositions.get(number).name().equals(name)) {
                return number;
            }
        }

        return -1;
    }

    // the number of the universe with the passed uuid, or -1
    public int universe(String uuid) {
        return uuid == null ? -1 : universes.indexOf(uuid);
    }

}
