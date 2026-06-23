package com.ascargon.rocketshow.lighting.designer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

public class FixtureModeChannelListDeserializer extends ValueDeserializer<List<FixtureModeChannel>> {

    @Override
    public List<FixtureModeChannel> deserialize(JsonParser jp, DeserializationContext ctxt) {
        List<FixtureModeChannel> result = new ArrayList<>();
        JsonNode node = ctxt.readTree(jp);

        for (JsonNode itemNode : node) {
            FixtureModeChannel fixtureModeChannel = new FixtureModeChannel();

            if (itemNode.isTextual()) {
                fixtureModeChannel.setName(itemNode.asText());
            } else if (itemNode.isObject()) {
                fixtureModeChannel = ctxt.readTreeAsValue(itemNode, FixtureModeChannel.class);
            }

            result.add(fixtureModeChannel);
        }

        return result;
    }
}