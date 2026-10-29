package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the projects and fixture profiles of the designer the way the designer reads them itself: a value which is
 * null keeps the default of its field (the designer writes null e.g. for a number it couldn't work out), and a name
 * this Rocket Show doesn't know (e.g. a capability type of a newer fixture library) is read as missing, instead of
 * leaving the whole project unplayable.
 *
 * @author Moritz A. Vieli
 */
public final class DesignerJson {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .changeDefaultNullHandling(handling -> JsonSetter.Value.forValueNulls(Nulls.SKIP))
            .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .build();

    private DesignerJson() {
    }

}
