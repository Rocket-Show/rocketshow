package com.ascargon.rocketshow.selftest;

import org.springframework.stereotype.Service;

/**
 * The unit checks its own hardware: audio, video, MIDI, GPIO, DMX and storage. See testing/README.md.
 */
@Service
public interface SelfTestService {

    SelfTestResult run(SelfTestRequest request) throws Exception;

}
