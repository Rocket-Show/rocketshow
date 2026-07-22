package com.ascargon.rocketshow.health;

import lombok.Getter;
import lombok.Setter;

/**
 * The result of a single check within a system self-test (e.g. MIDI loopback).
 */
@Getter
@Setter
public class SystemTestStep {

    private String name;

    // Whether the check passed. Skipped checks are also reported as successful so they
    // don't fail the overall self-test.
    private boolean success;

    // A check that could not be run because the required hardware/configuration was not
    // present (e.g. GPIO disabled). Skipped checks do not fail the overall self-test.
    private boolean skipped;

    // A human readable description of the outcome
    private String message;

    public SystemTestStep() {
    }

    public SystemTestStep(String name, boolean success, String message) {
        this.name = name;
        this.success = success;
        this.message = message;
    }

}
