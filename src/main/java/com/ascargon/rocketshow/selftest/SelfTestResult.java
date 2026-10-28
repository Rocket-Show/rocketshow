package com.ascargon.rocketshow.selftest;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SelfTestResult {

    // Failed, if any check failed. Skipped checks don't fail the self-test.
    private SelfTestOutcome result;
    private String startedAt;
    private long durationMillis;
    private String softwareVersion;
    private String serial;
    private List<SelfTestCheck> checks = new ArrayList<>();

}
