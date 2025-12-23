package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class DefaultHealthService implements HealthService {

	@Override
	public HealthStatus getHealthStatus() throws Exception {
        HealthStatus healthStatus = new HealthStatus();

        // TODO execute actions over HTTP localhost
        // TODO play the default composition and check for errors
        // TODO return free disk space and warn, if too low
        // TODO check for updates and warn, if no internet connection
        // TODO gather system information (os version, etc.)
        // TODO check for connected audio, HDMI, MIDI, DMX system (check healthiness of OLA)
        // TODO show GPIO status
        // TODO show networking info (WIFI, Accesspoint and ethernet)
        // TODO check the logs for errors (rocketshow, OLA and syslogs)

        return healthStatus;
	}

}
