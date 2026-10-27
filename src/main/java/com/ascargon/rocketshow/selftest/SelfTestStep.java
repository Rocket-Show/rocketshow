package com.ascargon.rocketshow.selftest;

import java.util.List;

/**
 * A part of the self-test, e.g. the MIDI checks. A step only reports its checks to the context.
 */
interface SelfTestStep {

    // The names of the checks the step reports
    List<String> getCheckNames();

    // Refuse a request the step can't run safely, before anything runs
    default void validate(SelfTestRequest request) {
    }

    // Before the checks of any step: suspend what the test signals of the step could set off, e.g. triggers
    default void prepare() {
    }

    void run(SelfTestContext context) throws Exception;

    // After the checks of all steps, also after a failing or throwing one: put back what the step changed
    default void restore() {
    }

}
