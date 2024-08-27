package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FixtureMatrixPixelGroupListDeserializer extends JsonDeserializer<List<FixtureMatrixPixelGroup>> {

    @Override
    public List<FixtureMatrixPixelGroup> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        List<FixtureMatrixPixelGroup> result = new ArrayList<>();
        JsonNode node = p.getCodec().readTree(p);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
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
            ArrayNode arrayNode = (ArrayNode) node;
            arrayNode.forEach(item -> list.add(item.asText()));
        }
        return list;
    }
}