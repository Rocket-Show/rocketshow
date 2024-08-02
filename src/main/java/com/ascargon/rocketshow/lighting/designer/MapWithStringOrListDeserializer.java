package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;

public class MapWithStringOrListDeserializer extends JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        Map<String, Object> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (valueNode.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode element : valueNode) {
                    list.add(element.asText());
                }
                result.put(key, list);
            } else if (valueNode.isTextual()) {
                result.put(key, valueNode.asText());
            } else {
                result.put(key, jp.getCodec().treeToValue(valueNode, Object.class));
            }
        }

        return result;
    }
}
