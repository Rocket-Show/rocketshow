package com.ascargon.rocketshow.selftest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * One run of the self-test: the request and the checks reported so far. The checks stop as soon as a composition
 * starts, because the self-test never plays over a show.
 */
class SelfTestContext {

    static final String STOPPED = "Stopped, because a composition started";

    private final SelfTestRequest request;
    private final BooleanSupplier compositionPlaying;
    private final List<SelfTestCheck> checks = new CopyOnWriteArrayList<>();
    private volatile boolean stopped = false;

    SelfTestContext(SelfTestRequest request, BooleanSupplier compositionPlaying) {
        this.request = request;
        this.compositionPlaying = compositionPlaying;
    }

    SelfTestRequest getRequest() {
        return request;
    }

    void report(SelfTestCheck check) {
        checks.add(check);
    }

    List<SelfTestCheck> getChecks() {
        return checks;
    }

    boolean isReported(String name) {
        return checks.stream().anyMatch(check -> check.getName().equals(name));
    }

    // Whether a composition started meanwhile
    boolean isStopped() {
        if (!stopped && compositionPlaying.getAsBoolean()) {
            stopped = true;
        }

        return stopped;
    }

    // Waits, but returns false as soon as a composition starts
    boolean await(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;

        while (!isStopped()) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                return true;
            }
            Thread.sleep(Math.min(remaining, 50));
        }

        return false;
    }

}
