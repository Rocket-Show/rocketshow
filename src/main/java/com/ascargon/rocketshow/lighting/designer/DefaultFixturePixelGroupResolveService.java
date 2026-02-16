package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

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

    // ---------- group resolution ----------
    @Override
    public List<CachedFixturePixel> resolveGroupPixels(
            FixtureMatrixPixelGroup group,
            List<CachedFixturePixel> allPixels
    ) {
        if (group == null || allPixels == null) return List.of();

        // 1) all
        if (group.isAll()) {
            return new ArrayList<>(allPixels);
        }

        FixtureMatrixPixelGroupConstraints c = group.getConstraints();
        if (c == null) return List.of();

        // 2) explicit keys (if present, treat as the definition)
        if (c.getKeys() != null && !c.getKeys().isEmpty()) {
            Set<String> wanted = new HashSet<>(c.getKeys());
            return allPixels.stream()
                    .filter(p -> wanted.contains(p.getKey()))
                    .collect(Collectors.toList());
        }

        // 3) constraints by x/y/z/name
        return allPixels.stream()
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
