package com.ascargon.rocketshow.api;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginThrottleService {

    private static final long MAX_DELAY_MS = 15_000;

    private int failures = 0;
    private Instant nextAllowedAttempt = Instant.EPOCH;

    public synchronized void delayIfNeeded() {
        Instant now = Instant.now();

        if (now.isBefore(nextAllowedAttempt)) {
            long sleepMs = Duration.between(now, nextAllowedAttempt).toMillis();

            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void onFailure() {
        failures++;

        long delayMs = computeDelayMillis(failures);
        nextAllowedAttempt = Instant.now().plusMillis(delayMs);
    }

    public synchronized void onSuccess() {
        failures = 0;
        nextAllowedAttempt = Instant.EPOCH;
    }

    private long computeDelayMillis(int failures) {
        if (failures <= 1) {
            return 0; // first failed login: no delay
        }

        // 2nd fail -> 1s, 3rd -> 2s, 4th -> 4s, 5th -> 8s ...
        long delay = 1000L << Math.min(failures - 2, 10);
        return Math.min(delay, MAX_DELAY_MS);
    }
}