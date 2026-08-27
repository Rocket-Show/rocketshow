package com.ascargon.rocketshow.scheduler;

import org.springframework.stereotype.Service;

/**
 * Start compositions based on a timer.
 */
@Service
public interface SchedulerService {

    /**
     * Apply the scheduled compositions from the settings. Already planned executions are discarded.
     */
    void reload();

}
