package com.ascargon.rocketshow.selftest;

import lombok.Getter;
import lombok.Setter;

/**
 * The result of one check of the self-test, e.g. the audio of one sound card.
 */
@Getter
@Setter
public class SelfTestCheck {

    private String name;
    // What was checked, e.g. the sound card, if the check runs more than once
    private String target;
    private SelfTestOutcome result;
    private String details;

    public SelfTestCheck() {
    }

    public SelfTestCheck(String name, String target, SelfTestOutcome result, String details) {
        this.name = name;
        this.target = target;
        this.result = result;
        this.details = details;
    }

    public static SelfTestCheck passed(String name, String target, String details) {
        return new SelfTestCheck(name, target, SelfTestOutcome.PASSED, details);
    }

    public static SelfTestCheck failed(String name, String target, String details) {
        return new SelfTestCheck(name, target, SelfTestOutcome.FAILED, details);
    }

    public static SelfTestCheck skipped(String name, String target, String details) {
        return new SelfTestCheck(name, target, SelfTestOutcome.SKIPPED, details);
    }

    @Override
    public String toString() {
        return name + (target == null ? "" : " (" + target + ")") + ": " + result + (details == null ? "" : ", " + details);
    }

}
