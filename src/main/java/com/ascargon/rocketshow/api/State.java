package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.update.UpdateService;

import com.ascargon.rocketshow.update.UpdateState;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class State {

    private Integer currentCompositionIndex;
    private CompositionPlayer.PlayState playState;
    private String currentCompositionName;
    private Long currentCompositionDurationMillis;
    private Long positionMillis;
    private UpdateState updateState;
    private String currentSetName;
    private String error;

}
