package com.ascargon.rocketshow.health;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The aggregated result of a system self-test, consisting of one {@link SystemTestStep}
 * per checked subsystem (composition playback, GPIO, MIDI, ...).
 */
@Getter
@Setter
public class SystemTestResult {

    // True as long as no executed (non-skipped) check failed
    private boolean success = true;

    private List<SystemTestStep> steps = new ArrayList<>();

    public void addStep(SystemTestStep step) {
        steps.add(step);

        if (!step.isSkipped() && !step.isSuccess()) {
            success = false;
        }
    }

}
