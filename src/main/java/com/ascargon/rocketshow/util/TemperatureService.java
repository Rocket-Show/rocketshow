package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Returns the current device temperature.
 *
 * @author Moritz A. Vieli
 */
@Service
public interface TemperatureService {

    Double get() throws Exception;

}
