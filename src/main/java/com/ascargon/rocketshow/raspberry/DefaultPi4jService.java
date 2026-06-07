package com.ascargon.rocketshow.raspberry;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

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
