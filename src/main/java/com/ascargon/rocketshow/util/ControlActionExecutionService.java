package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.PlayerService;
import org.springframework.stereotype.Service;

@Service
public interface ControlActionExecutionService {

    void execute(ControlAction controlAction) throws Exception;

}
