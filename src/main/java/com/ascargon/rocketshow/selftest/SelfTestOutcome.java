package com.ascargon.rocketshow.selftest;

public enum SelfTestOutcome {
    PASSED,
    FAILED,
    // The unit doesn't have the hardware, e.g. no loopback cable
    SKIPPED
}
