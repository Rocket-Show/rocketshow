package com.ascargon.rocketshow.selftest;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * What a self-test needs to know about the unit's surroundings, e.g. the loopback cables of a factory fixture.
 */
@Getter
@Setter
public class SelfTestRequest {

    // Checks which must pass: skipping one (e.g. no loopback cable) fails it
    private List<String> expect = new ArrayList<>();

    // GPIO pins connected to each other
    private List<GpioLoopback> gpioLoopback = new ArrayList<>();

    @Getter
    @Setter
    public static class GpioLoopback {

        private Integer out;
        private Integer in;

        public GpioLoopback() {
        }

        public GpioLoopback(Integer out, Integer in) {
            this.out = out;
            this.in = in;
        }

    }

}
