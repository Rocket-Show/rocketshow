package com.ascargon.rocketshow.lighting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class DefaultOlaService implements OlaService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultOlaService.class);

    public String getHealthyError() {
        try {
            // 1) systemctl is-active olad
            String serviceState = runCommand("systemctl", "is-active", "olad");
            if (serviceState.isBlank()) {
                return "OLA service check returned no output";
            }
            if (!"active".equals(serviceState.trim())) {
                return "OLA service is not active: " + serviceState.trim();
            }

            // 2) pgrep olad
            String processOutput = runCommand("pgrep", "-a", "olad");
            if (processOutput.isBlank()) {
                return "OLA process is not running";
            }

            // 3) ss -ltnp | grep 9090
            String portOutput = runShellCommand("ss -ltnp | grep 9090");
            if (portOutput.isBlank()) {
                return "OLA port 9090 is not listening";
            }

            return null; // everything fine
        } catch (Exception e) {
            return "OLA health check failed: " + e.getMessage();
        }
    }

    private static String runCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        process.waitFor();
        return output.toString().trim();
    }

    private static String runShellCommand(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        process.waitFor();
        return output.toString().trim();
    }

}
