package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface ActionExecutionService {

    void execute(Action action) throws Exception;

    void executeFromTrigger(ActionTrigger actionTrigger) throws Exception;

    // The key or button of the trigger was let go: the scenes it flashes turn off again
    void releaseFromTrigger(ActionTrigger actionTrigger);

}
