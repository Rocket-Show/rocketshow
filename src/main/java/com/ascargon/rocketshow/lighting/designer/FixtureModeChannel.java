package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * A Rocket Show Designer fixture mode channel (e.g. a matrix channel).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureModeChannel {

    private String insert;
    // the list of string could be a single string (no JSON array), e.g. 'eachPixelABC'
    @JsonDeserialize(using = StringOrListDeserializer.class)
    private List<String> repeatFor;
    private String channelOrder;
    private List<String> templateChannels;

    public String getInsert() {
        return insert;
    }

    public void setInsert(String insert) {
        this.insert = insert;
    }

    public List<String> getRepeatFor() {
        return repeatFor;
    }

    public void setRepeatFor(List<String> repeatFor) {
        this.repeatFor = repeatFor;
    }

    public String getChannelOrder() {
        return channelOrder;
    }

    public void setChannelOrder(String channelOrder) {
        this.channelOrder = channelOrder;
    }

    public List<String> getTemplateChannels() {
        return templateChannels;
    }

    public void setTemplateChannels(List<String> templateChannels) {
        this.templateChannels = templateChannels;
    }
}
