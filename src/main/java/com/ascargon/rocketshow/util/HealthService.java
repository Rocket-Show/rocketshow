package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface HealthService {

    HealthStatus getHealthStatus();

    void testSystem();

}