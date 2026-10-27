package com.ascargon.rocketshow.lighting.designer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DefaultFixturePixelGroupResolveService implements FixturePixelGroupResolveService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultFixturePixelGroupResolveService.class);

    private static final Pattern COMP_PATTERN = Pattern.compile("^(<=|>=|=)\\s*(-?\\d+)$");
    private static final Pattern MOD_PATTERN = Pattern.compile("^(\\d+)n(?:\\+(\\d+))?$");

    // ---------- numeric rule matching (<=5, >=5, =5, even, odd, 3n, 3n+1, plain number) ----------
    private boolean matchNumberRule(int value, String ruleRaw) {
        if (ruleRaw == null) throw new IllegalArgumentException("Numeric rule is null");
        String rule = ruleRaw.trim().toLowerCase(Locale.ROOT);

        // comparisons: <=5, >=5, =5
        Matcher m = COMP_PATTERN.matcher(rule);
        if (m.matches()) {
            String op = m.group(1);
            int n = Integer.parseInt(m.group(2));
            return switch (op) {
                case "<=" -> value <= n;
                case ">=" -> value >= n;
                case "=" -> value == n;
                default -> throw new IllegalStateException("Unexpected operator: " + op);
            };
        }

        // even / odd
        if (rule.equals("even")) return value % 2 == 0;
        if (rule.equals("odd")) return Math.abs(value % 2) == 1;

        // 3n, 3n+1, ...
        Matcher mn = MOD_PATTERN.matcher(rule);
        if (mn.matches()) {
            int base = Integer.parseInt(mn.group(1)); // e.g. 3
            int rem = (mn.group(2) == null) ? 0 : Integer.parseInt(mn.group(2)); // e.g. 1

            int mod = ((value % base) + base) % base;
            int target = ((rem % base) + base) % base;
            return mod == target;
        }

        // plain number => "=number"
        try {
            int asNum = Integer.parseInt(rule);
            return value == asNum;
        } catch (NumberFormatException ignored) {
            // fall through
        }

        throw new IllegalArgumentException("Unknown numeric rule: \"" + ruleRaw + "\"");
    }

    // ---------- constraint matching ----------
    private boolean matchesAllConstraints(CachedFixturePixel p, FixtureMatrixPixelGroupConstraints c) {
        if (c == null) return true;

        if (c.getX() != null) {
            for (String rule : c.getX()) if (!matchNumberRule(p.getX(), rule)) return false;
        }
        if (c.getY() != null) {
            for (String rule : c.getY()) if (!matchNumberRule(p.getY(), rule)) return false;
        }
        if (c.getZ() != null) {
            for (String rule : c.getZ()) if (!matchNumberRule(p.getZ(), rule)) return false;
        }

        if (c.getName() != null) {
            // AND across regexes (same as TS)
            for (String rxStr : c.getName()) {
                Pattern rx = Pattern.compile(rxStr);
                if (!rx.matcher(p.getKey()).find()) return false;
            }
        }

        return true;
    }

    private FixtureMatrixPixelGroup findGroupByName(List<FixtureMatrixPixelGroup> groups, String name) {
        if (groups == null) return null;
        for (FixtureMatrixPixelGroup g : groups) {
            if (g != null && name.equals(g.getName())) return g;
        }
        return null;
    }

    // Pixel keys like the fixture library sorts them: numbers by their value (2 before 10), the rest alphabetically
    static int compareKeys(String key1, String key2) {
        int index1 = 0;
        int index2 = 0;

        while (index1 < key1.length() && index2 < key2.length()) {
            char char1 = key1.charAt(index1);
            char char2 = key2.charAt(index2);

            if (Character.isDigit(char1) && Character.isDigit(char2)) {
                int start1 = index1;
                int start2 = index2;
                while (index1 < key1.length() && Character.isDigit(key1.charAt(index1))) index1++;
                while (index2 < key2.length() && Character.isDigit(key2.charAt(index2))) index2++;

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

    private static List<CachedFixturePixel> sortedByKey(List<CachedFixturePixel> pixels) {
        List<CachedFixturePixel> result = new ArrayList<>(pixels);
        result.sort((pixel1, pixel2) -> compareKeys(pixel1.getKey(), pixel2.getKey()));
        return result;
    }

    // ---------- group resolution ----------
    // The pixels of a group in the order of the fixture library: the listed keys as listed, all pixels and pixels
    // constrained by their name alphanumerically, the others by their position (X changes fastest). The passed pixels
    // come in this order already.
    @Override
    public List<CachedFixturePixel> resolveGroupPixels(
            FixtureMatrixPixelGroup group,
            List<CachedFixturePixel> allPixels
    ) {
        if (group == null || allPixels == null) return List.of();

        // 1) all
        if (group.isAll()) {
            return sortedByKey(allPixels);
        }

        FixtureMatrixPixelGroupConstraints c = group.getConstraints();
        if (c == null) return List.of();

        // 2) explicit keys (if present, treat as the definition)
        if (c.getKeys() != null && !c.getKeys().isEmpty()) {
            List<CachedFixturePixel> result = new ArrayList<>();
            for (String key : c.getKeys()) {
                allPixels.stream().filter(p -> key.equals(p.getKey())).findFirst().ifPresent(result::add);
            }
            return result;
        }

        // 3) constraints by x/y/z/name
        List<CachedFixturePixel> candidates = c.getName() != null ? sortedByKey(allPixels) : allPixels;
        return candidates.stream()
                .filter(p -> matchesAllConstraints(p, c))
                .collect(Collectors.toList());
    }

    @Override
    public List<CachedFixturePixel> getPixelsForKeyOrGroup(
            FixtureMatrix matrix,
            List<CachedFixturePixel> allPixels,
            String keyOrGroup
    ) {
        if (matrix == null || allPixels == null || keyOrGroup == null) return List.of();

        // 1) exact key first
        for (CachedFixturePixel p : allPixels) {
            if (keyOrGroup.equals(p.getKey())) return List.of(p);
        }

        // 2) group by name
        FixtureMatrixPixelGroup group = findGroupByName(matrix.getPixelGroups(), keyOrGroup);
        if (group == null) return List.of();

        return resolveGroupPixels(group, allPixels);
    }

}
