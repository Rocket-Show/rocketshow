package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;

public class ListWithStringOrFixtureModeChannelDeserializer extends JsonDeserializer<List<Object>> {

    @Override
    public List<Object> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        List<Object> result = new ArrayList<>();
        JsonNode node = jp.getCodec().readTree(jp);

        for (JsonNode itemNode : node) {
            if (itemNode.isTextual()) {
                result.add(itemNode.asText());
            } else if (itemNode.isObject()) {
                FixtureModeChannel fixtureModeChannel = jp.getCodec().treeToValue(itemNode, FixtureModeChannel.class);
                result.add(fixtureModeChannel);
            }
        }
        return result;
    }
}
