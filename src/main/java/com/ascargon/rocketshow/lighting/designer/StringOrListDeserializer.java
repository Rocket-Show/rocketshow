package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StringOrListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
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