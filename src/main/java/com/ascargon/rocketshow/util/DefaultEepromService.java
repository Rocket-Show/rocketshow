package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultEepromService implements EepromService {

    private static final Pattern VERSION_DATE_PATTERN = Pattern.compile("\\b(\\d{4})[/-](\\d{2})[/-](\\d{2})\\b");

    static LocalDate parseVersionDate(String output) {
        Matcher matcher = VERSION_DATE_PATTERN.matcher(output);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No EEPROM version date found");
        }

        return LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        );
    }

    @Override
    public LocalDate getVersionDate() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("vcgencmd", "bootloader_version");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("vcgencmd exited with code " + exitCode);
        }

        return parseVersionDate(output.toString());
    }

}
