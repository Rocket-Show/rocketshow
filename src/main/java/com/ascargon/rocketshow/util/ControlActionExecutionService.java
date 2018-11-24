package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface ControlActionExecutionService {

    void execute(ControlAction controlAction) throws Exception;

}
