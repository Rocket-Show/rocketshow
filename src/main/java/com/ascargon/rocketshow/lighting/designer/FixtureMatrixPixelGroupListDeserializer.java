package com.ascargon.rocketshow.lighting.designer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.io.IOException;
import java.util.*;

public class FixtureMatrixPixelGroupListDeserializer extends ValueDeserializer<List<FixtureMatrixPixelGroup>> {

    @Override
    public List<FixtureMatrixPixelGroup> deserialize(JsonParser p, DeserializationContext ctxt) {
        List<FixtureMatrixPixelGroup> result = new ArrayList<>();
        JsonNode node = ctxt.readTree(p);

        Set<Map.Entry<String, JsonNode>> fields = node.properties();

        for (Map.Entry<String, JsonNode> entry : fields) {
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();
            FixtureMatrixPixelGroup group = new FixtureMatrixPixelGroup();

            group.setName(key);

            if (valueNode.isTextual() && "all".equals(valueNode.asText())) {
                group.setAll(true);
            } else if (valueNode.isArray()) {
                group.getConstraints().setKeys(convertToList(valueNode));
            } else if (valueNode.isObject()) {
                if (valueNode.has("x")) {
                    group.getConstraints().setX(convertToList(valueNode.get("x")));
                }
                if (valueNode.has("y")) {
                    group.getConstraints().setY(convertToList(valueNode.get("y")));
                }
                if (valueNode.has("z")) {
                    group.getConstraints().setZ(convertToList(valueNode.get("z")));
                }
                if (valueNode.has("name")) {
                    group.getConstraints().setName(convertToList(valueNode.get("name")));
                }
            }

            result.add(group);
        }

        return result;
    }

    private List<String> convertToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
}