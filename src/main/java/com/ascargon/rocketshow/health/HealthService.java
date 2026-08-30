package com.ascargon.rocketshow.health;

import org.springframework.stereotype.Service;

@Service
public interface HealthService {

    HealthStatus getHealthStatus();

    SystemTestResult testSystem();

}