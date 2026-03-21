package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class DefaultHealthService implements HealthService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultHealthService.class);

    @Override
    public HealthStatus getHealthStatus() {
        HealthStatus healthStatus = new HealthStatus();

        // TODO allow without security when setup is not yet finished (no admin pw yet). Afterwards, role ADMIN is required

        // TODO show serial, SKU or whether no data is available
        // TODO check for updates and warn, if no internet connection
        // TODO gather system information (os version, disk space, memory, etc.)
        // TODO check for connected audio, HDMI, MIDI, DMX system (check healthiness of OLA)
        // TODO show GPIO input status
        // TODO show networking info (WIFI, Accesspoint and ethernet)
        // TODO check the logs for errors (rocketshow, OLA and syslogs)
        // TODO show connected USB devices

        return healthStatus;
    }

    @Override
    public void testSystem() {
        // TODO allow without security when setup is not yet finished (no admin pw yet). Afterwards, role ADMIN is required

        // TODO execute actions over HTTP localhost
        // TODO play the default composition and check for errors
        // TODO periodically change GPIO output status (e.g. each 3 seconds)
    }

}