package com.ascargon.rocketshow.health;

import org.springframework.stereotype.Service;

/**
 * Run an on-device self-test, exercising the connected hardware (audio/video via the
 * default composition, GPIO outputs and MIDI) and reporting the result per subsystem.
 */
@Service
public interface SystemTestService {

    SystemTestResult runSystemTest();

}
