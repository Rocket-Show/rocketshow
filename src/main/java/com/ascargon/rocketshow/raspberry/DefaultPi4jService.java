package com.ascargon.rocketshow.raspberry;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

@Service
public class DefaultPi4jService implements Pi4jService {

    private final Context pi4j;

    public DefaultPi4jService() {
        // Initialize the Pi4J instance
        pi4j = Pi4J.newAutoContext();
    }

    @Override
    public Context getContext() {
        return pi4j;
    }

    @PreDestroy
    public void close() {
        if (pi4j != null) {
            pi4j.shutdown();
        }
    }
}
