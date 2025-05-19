package com.ascargon.rocketshow.lighting;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LightingActionUniverse {

    private String universeName;
    private List<LightingActionChannelValue> channelValueList = new ArrayList<>();

}
