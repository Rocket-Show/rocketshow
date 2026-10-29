package com.ascargon.rocketshow.lighting.designer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.*;
import java.util.regex.Pattern;

// Reads the pixel groups of a matrix the way the designer reads them (FixtureMatrix and FixtureMatrixPixelGroup of the
// designer), so a project plays its pixel groups the same on stage as in the browser.
public class FixtureMatrixPixelGroupListDeserializer extends ValueDeserializer<List<FixtureMatrixPixelGroup>> {

    // a name the browser takes for the index of an array (e.g. "2", but not "02" or "-1")
    private static final Pattern ARRAY_INDEX = Pattern.compile("0|[1-9]\\d{0,9}");
    private static final long MAX_ARRAY_INDEX = 4294967294L;

    @Override
    public List<FixtureMatrixPixelGroup> deserialize(JsonParser p, DeserializationContext ctxt) {
        List<FixtureMatrixPixelGroup> result = new ArrayList<>();
        JsonNode node = ctxt.readTree(p);

        for (Map.Entry<String, JsonNode> entry : inBrowserOrder(node.properties())) {
            result.add(readGroup(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    // The groups in the order the browser keeps the names of an object in: the names of array indices (e.g. "1", "2")
    // first and by their number, all other names as they are written. The designer (and the fixture library) take the
    // groups in this order, e.g. for the channels of "eachPixelGroup", and save the project with it.
    static List<Map.Entry<String, JsonNode>> inBrowserOrder(Set<Map.Entry<String, JsonNode>> properties) {
        List<Map.Entry<String, JsonNode>> indices = new ArrayList<>();
        List<Map.Entry<String, JsonNode>> names = new ArrayList<>();

        for (Map.Entry<String, JsonNode> property : properties) {
            if (isArrayIndex(property.getKey())) {
                indices.add(property);
            } else {
                names.add(property);
            }
        }

        indices.sort(Comparator.comparingLong(property -> Long.parseLong(property.getKey())));
        indices.addAll(names);

        return indices;
    }

    private static boolean isArrayIndex(String name) {
        return ARRAY_INDEX.matcher(name).matches() && Long.parseLong(name) <= MAX_ARRAY_INDEX;
    }

    // what the browser takes for false in a condition
    private static boolean isFalsy(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                || (node.isBoolean() && !node.booleanValue())
                || (node.isNumber() && node.doubleValue() == 0)
                || (node.isTextual() && node.asText().isEmpty());
    }

    private static boolean isTrue(JsonNode node) {
        return node != null && node.isBoolean() && node.booleanValue();
    }

    // A group like the fixture library defines it: "all", an array of pixel keys or an object of constraints. Projects
    // of earlier designers stored it wrapped as { isAll: <definition>, constraints: null } (also wrapped several times).
    // A group without a definition has no pixels.
    static FixtureMatrixPixelGroup readGroup(String name, JsonNode data) {
        FixtureMatrixPixelGroup group = new FixtureMatrixPixelGroup();
        group.setName(name);

        while (data != null && data.isObject() && data.has("isAll")) {
            JsonNode isAll = data.get("isAll");

            if (isTrue(isAll)) {
                break;
            }

            data = isFalsy(isAll) ? data.get("constraints") : isAll;
        }

        if (isFalsy(data)) {
            group.setConstraints(null);
            return group;
        }

        if ((data.isTextual() && "all".equals(data.asText())) || (data.isObject() && isTrue(data.get("isAll")))) {
            group.setAll(true);
            group.setConstraints(null);
            return group;
        }

        FixtureMatrixPixelGroupConstraints constraints = new FixtureMatrixPixelGroupConstraints();

        if (data.isArray()) {
            // an array of pixel keys
            constraints.setKeys(convertToList(data));
        } else if (data.isObject()) {
            // an object of constraints (designers before also stored the empty keys)
            constraints.setX(convertToList(data.get("x")));
            constraints.setY(convertToList(data.get("y")));
            constraints.setZ(convertToList(data.get("z")));
            constraints.setName(convertToList(data.get("name")));

            List<String> keys = convertToList(data.get("keys"));

            if (!keys.isEmpty()) {
                constraints.setKeys(keys);
            }
        }

        group.setConstraints(constraints);
        return group;
    }

    private static List<String> convertToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
}
