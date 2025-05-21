package com.ascargon.rocketshow.raspberry;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.plugin.raspberrypi.platform.RaspberryPiPlatform;
import com.pi4j.plugin.raspberrypi.provider.gpio.digital.RpiDigitalInputProvider;
import com.pi4j.plugin.raspberrypi.provider.gpio.digital.RpiDigitalOutputProvider;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

@Service
public class DefaultPi4jService implements Pi4jService {

    private Context pi4j;

    @Override
    public Context getContext() {
        if (pi4j == null) {
            // Initialize the Pi4J instance
            pi4j = Pi4J.newAutoContext();
        }

        return pi4j;
    }

    @PreDestroy
    public void close() {
        if (pi4j != null) {
            pi4j.shutdown();
        }
    }
}
