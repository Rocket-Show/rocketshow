package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import org.springframework.stereotype.Service;

@Service
public interface StateService {

    State getCurrentState(PlayerService playerService, SetService setService, CompositionService compositionService);

}
