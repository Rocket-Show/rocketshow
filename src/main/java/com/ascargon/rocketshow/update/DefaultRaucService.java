package com.ascargon.rocketshow.update;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultRaucService implements RaucService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaucService.class);

    private final UpdateNotificationService updateNotificationService;

    // Installing a bundle can fail transiently (e.g. "Failed mounting bundle:
    // Failed to load dm table" while setting up dm-verity). Retrying the same
    // bundle usually succeeds immediately, so we attempt the installation
    // multiple times before giving up.
    private final static int MAX_INSTALL_ATTEMPTS = 5;
    private final static long INSTALL_RETRY_DELAY_MILLIS = 5000;

    public DefaultRaucService(
            UpdateNotificationService updateNotificationService
    ) {
        this.updateNotificationService = updateNotificationService;
    }

    @Override
    public String getCurrentSlot() {
        try {
            ShellManager shellManager = new ShellManager(
                    new String[]{"rauc", "status"}
            );

            Process process = shellManager.getProcess();

            // Read full output
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            process.waitFor();
            shellManager.close();

            // Parse "Booted from:" line
            for (String line : output.split("\\R")) {
                line = line.trim();
                if (line.startsWith("Booted from:")) {
                    // Extract first token after colon
                    String value = line.substring("Booted from:".length()).trim();
                    return value.split("\\s+")[0]; // e.g. "rootfs.0"
                }
            }

        } catch (Exception e) {
            logger.error("Could not read current RAUC slot", e);
        }

        return null;
    }

    @Override
    public void installBundle(String url) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_INSTALL_ATTEMPTS; attempt++) {
            try {
                attemptInstallBundle(url);
                // Installation succeeded
                return;
            } catch (Exception e) {
                lastException = e;

                if (attempt < MAX_INSTALL_ATTEMPTS) {
                    logger.warn("RAUC bundle installation failed (attempt {} of {}). Retrying in {} ms...",
                            attempt, MAX_INSTALL_ATTEMPTS, INSTALL_RETRY_DELAY_MILLIS, e);
                    Thread.sleep(INSTALL_RETRY_DELAY_MILLIS);
                } else {
                    logger.error("RAUC bundle installation failed after {} attempts.", MAX_INSTALL_ATTEMPTS);
                }
            }
        }

        throw lastException;
    }

    private void attemptInstallBundle(String url) throws Exception {
        Pattern PROGRESS_PATTERN =
                Pattern.compile("^\\s*(\\d{1,3})%\\s+(.*?)(?:\\s+done\\.)?\\s*$");

        ShellManager shellManager = new ShellManager(
                new String[]{"rauc", "install", url}
        );

        Process process = shellManager.getProcess();

        int lastPercentage = -1;
        String lastMessage = null;

        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("RAUC output: {}", line);

                    Matcher matcher = PROGRESS_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        lastPercentage = Integer.parseInt(matcher.group(1));
                        lastMessage = matcher.group(2).trim();

                        UpdateState updateState = new UpdateState();
                        updateState.setProgressPercentage(lastPercentage);
                        updateState.setProgressMessage(lastMessage);
                        updateNotificationService.notifyClients(updateState);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new Exception("RAUC exited with exit code " + exitCode);
            }
        } finally {
            shellManager.close();
        }
    }

    @Override
    public void markGood() throws InterruptedException, IOException {
        ShellManager shellManager = new ShellManager(new String[]{"rauc", "status", "mark-good"});
        shellManager.getProcess().waitFor();
        shellManager.close();
    }

}
