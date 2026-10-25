package com.ascargon.rocketshow.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleServiceTest {

    private long getDelayMillis(LoginThrottleService loginThrottleService) {
        long start = System.nanoTime();
        loginThrottleService.delayIfNeeded();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void doublesTheDelayUpToFifteenSeconds() {
        assertEquals(0, LoginThrottleService.computeDelayMillis(1));
        assertEquals(1000, LoginThrottleService.computeDelayMillis(2));
        assertEquals(2000, LoginThrottleService.computeDelayMillis(3));
        assertEquals(4000, LoginThrottleService.computeDelayMillis(4));
        assertEquals(8000, LoginThrottleService.computeDelayMillis(5));
        assertEquals(15000, LoginThrottleService.computeDelayMillis(6));
        assertEquals(15000, LoginThrottleService.computeDelayMillis(100));
    }

    @Test
    void doesNotDelayAfterTheFirstFailure() {
        LoginThrottleService loginThrottleService = new LoginThrottleService();
        loginThrottleService.onFailure();

        assertTrue(getDelayMillis(loginThrottleService) < 500);
    }

    @Test
    void delaysTheNextLoginAfterFailures() {
        LoginThrottleService loginThrottleService = new LoginThrottleService();
        loginThrottleService.onFailure();
        loginThrottleService.onFailure();

        assertTrue(getDelayMillis(loginThrottleService) >= 900);
    }

    @Test
    void successfulLoginEndsTheDelay() {
        LoginThrottleService loginThrottleService = new LoginThrottleService();
        loginThrottleService.onFailure();
        loginThrottleService.onFailure();
        loginThrottleService.onFailure();
        loginThrottleService.onSuccess();

        assertTrue(getDelayMillis(loginThrottleService) < 500);
    }

}
