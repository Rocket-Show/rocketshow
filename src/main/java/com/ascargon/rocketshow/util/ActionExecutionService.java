package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface ActionExecutionService {

    void execute(Action action) throws Exception;

    void executeFromTrigger(ActionTrigger actionTrigger) throws Exception;

}
