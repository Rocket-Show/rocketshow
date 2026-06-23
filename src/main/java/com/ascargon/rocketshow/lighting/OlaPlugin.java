package com.ascargon.rocketshow.lighting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A response port we got from a OLA port list query.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class OlaPlugin implements Cloneable {

    private Integer id;
    private String name;
    private List<OlaPlugin> conflictList = new ArrayList<>();

    @Override
    public OlaPlugin clone() {
        try {
            OlaPlugin cloned = (OlaPlugin) super.clone();
            // Deep copy of conflictList
            cloned.conflictList = new ArrayList<>();
            for (OlaPlugin conflict : this.conflictList) {
                cloned.conflictList.add(conflict.clone());
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Should not happen, since we implement Cloneable
        }
    }

}
