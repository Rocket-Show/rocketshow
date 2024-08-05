package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.*;

public class FixturePixelGroupMapDeserializer extends JsonDeserializer<List<FixtureProfileMatrixPixelGroup>> {

    @Override
    public List<FixtureProfileMatrixPixelGroup> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        List<FixtureProfileMatrixPixelGroup> result = new ArrayList<>();
        JsonNode node = p.getCodec().readTree(p);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();
            FixtureProfileMatrixPixelGroup group = new FixtureProfileMatrixPixelGroup();

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