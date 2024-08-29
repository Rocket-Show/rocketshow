package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;

public class FixtureModeChannelListDeserializer extends JsonDeserializer<List<FixtureModeChannel>> {

    @Override
    public List<FixtureModeChannel> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        List<FixtureModeChannel> result = new ArrayList<>();
        JsonNode node = jp.getCodec().readTree(jp);

        for (JsonNode itemNode : node) {
            FixtureModeChannel fixtureModeChannel = new FixtureModeChannel();

            if (itemNode.isTextual()) {
                fixtureModeChannel.setName(itemNode.asText());
            } else if (itemNode.isObject()) {
                fixtureModeChannel = jp.getCodec().treeToValue(itemNode, FixtureModeChannel.class);
            }

            result.add(fixtureModeChannel);
        }

        return result;
    }
}
