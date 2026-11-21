package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Writes a project as the designer writes it, for the shared lighting engine, whose compiler reads the JSON of the
 * designer (see docs/lighting-engine.md): the project is migrated here first, then written with this. Written with
 * DesignerJson, a project keeps everything the designer saved, but its fixture profiles are written another way (the
 * channels, template channels and wheels of a profile unwrapped into it, a channel of a mode as an object, the pixel
 * groups of a matrix as a list): here they
 * are written as the designer and the fixture library write them. What is null is left out, which the compiler reads
 * the same (like DesignerJson, it keeps the default for a null).
 */
public final class DesignerProjectJson {

    // the channels, template channels and wheels of a profile in objects of their own, as the designer writes them
    abstract static class FixtureProfileMixIn {

        @JsonUnwrapped(enabled = false)
        @JsonProperty("availableChannels")
        FixtureProfileAvailableChannels availableChannels;

        @JsonUnwrapped(enabled = false)
        @JsonProperty("templateChannels")
        FixtureProfileTemplateChannels templateChannels;

        @JsonUnwrapped(enabled = false)
        @JsonProperty("wheels")
        FixtureProfileWheels wheels;

    }

    abstract static class FixtureMatrixMixIn {

        @JsonSerialize(using = PixelGroupsSerializer.class)
        List<FixtureMatrixPixelGroup> pixelGroups;

    }

    // the pixel groups of a matrix as the fixture library writes them: an object of the groups by their names, each
    // "all", an array of pixel keys or an object of constraints (see FixtureMatrixPixelGroupListDeserializer)
    static class PixelGroupsSerializer extends ValueSerializer<List<FixtureMatrixPixelGroup>> {

        @Override
        public void serialize(List<FixtureMatrixPixelGroup> groups, JsonGenerator generator, SerializationContext context) {
            generator.writeStartObject();

            for (FixtureMatrixPixelGroup group : groups) {
                generator.writeName(group.getName());
                FixtureMatrixPixelGroupConstraints constraints = group.getConstraints();

                if (group.isAll()) {
                    generator.writeString("all");
                } else if (constraints == null) {
                    generator.writeNull();
                } else if (isEmpty(constraints.getX()) && isEmpty(constraints.getY()) && isEmpty(constraints.getZ()) && isEmpty(constraints.getName())) {
                    writeStrings(generator, constraints.getKeys());
                } else {
                    generator.writeStartObject();
                    writeConstraint(generator, "x", constraints.getX());
                    writeConstraint(generator, "y", constraints.getY());
                    writeConstraint(generator, "z", constraints.getZ());
                    writeConstraint(generator, "name", constraints.getName());
                    writeConstraint(generator, "keys", constraints.getKeys());
                    generator.writeEndObject();
                }
            }

            generator.writeEndObject();
        }

        private static boolean isEmpty(List<String> list) {
            return list == null || list.isEmpty();
        }

        private static void writeConstraint(JsonGenerator generator, String name, List<String> values) {
            if (!isEmpty(values)) {
                generator.writeName(name);
                writeStrings(generator, values);
            }
        }

    }

    private static void writeStrings(JsonGenerator generator, List<String> values) {
        generator.writeStartArray();

        for (String value : values == null ? List.<String>of() : values) {
            generator.writeString(value);
        }

        generator.writeEndArray();
    }

    abstract static class FixtureModeMixIn {

        @JsonSerialize(contentUsing = ModeChannelSerializer.class)
        List<FixtureModeChannel> channels;

    }

    // a channel of a mode as the fixture library writes it: its name, null for a channel not used, or an object for the
    // channels of a matrix (see FixtureModeChannelListDeserializer)
    static class ModeChannelSerializer extends ValueSerializer<FixtureModeChannel> {

        @Override
        public void serialize(FixtureModeChannel channel, JsonGenerator generator, SerializationContext context) {
            boolean named = channel.getName() != null && !channel.getName().isEmpty();
            boolean matrix = channel.getInsert() != null || (channel.getRepeatFor() != null && !channel.getRepeatFor().isEmpty());

            if (named && !matrix) {
                generator.writeString(channel.getName());
            } else if (!matrix) {
                generator.writeNull();
            } else {
                generator.writeStartObject();

                if (named) {
                    generator.writeStringProperty("name", channel.getName());
                }

                if (channel.getInsert() != null) {
                    generator.writeStringProperty("insert", channel.getInsert());
                }

                generator.writeName("repeatFor");
                writeStrings(generator, channel.getRepeatFor());

                if (channel.getChannelOrder() != null) {
                    generator.writeStringProperty("channelOrder", channel.getChannelOrder());
                }

                generator.writeName("templateChannels");
                writeStrings(generator, channel.getTemplateChannels());
                generator.writeEndObject();
            }
        }

    }

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .addMixIn(FixtureProfile.class, FixtureProfileMixIn.class)
            .addMixIn(FixtureMode.class, FixtureModeMixIn.class)
            .addMixIn(FixtureMatrix.class, FixtureMatrixMixIn.class)
            .build();

    private DesignerProjectJson() {
    }

    /**
     * The JSON of the passed project as the designer writes it.
     */
    public static String write(Project project) {
        return MAPPER.writeValueAsString(project);
    }

}
