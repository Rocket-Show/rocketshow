package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Returns the used and available disk space.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultTemperatureService implements TemperatureService {

    @Override
    public Double get() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("vcgencmd", "measure_temp");
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = br.readLine()) != null) {
                // Expected format: temp=48.7'C
                line = line.trim();

                if (line.startsWith("temp=") && line.endsWith("'C")) {
                    String value = line
                            .substring("temp=".length(), line.length() - 2)
                            .trim();

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("vcgencmd exited with code " + exitCode);
                    }

                    return Double.parseDouble(value);
                }
            }
        }

        int exitCode = process.waitFor();
        throw new RuntimeException(
                "Could not read temperature. Exit code=" + exitCode);
    }

}
