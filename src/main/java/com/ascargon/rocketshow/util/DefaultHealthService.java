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
        // TODO check for updates and warn, if no internet connection
        // TODO gather system information (os version, disk space, memory, etc.)
        // TODO check for connected audio, HDMI, MIDI, DMX system (check healthiness of OLA)
        // TODO show GPIO input status
        // TODO show networking info (WIFI, Accesspoint and ethernet)
        // TODO check the logs for errors (rocketshow, OLA and syslogs)
        // TODO periodically change GPIO output status (e.g. each 3 seconds)
        // TODO secure reset button and check for status
        // TODO show connected USB devices

        return healthStatus;
	}

}
