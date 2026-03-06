package com.ascargon.rocketshow.lighting.designer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

public class StringOrListDeserializer extends ValueDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser jp, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(jp);
        List<String> result = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode element : node) {
                result.add(element.asText());
            }
        } else if (node.isTextual()) {
            result.add(node.asText());
        }

        return result;
    }
}