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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultRaucService implements RaucService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaucService.class);

    private final static Pattern PROGRESS_PATTERN = Pattern.compile("^\\s*(\\d{1,3})%\\s+(.*?)(?:\\s+done\\.)?\\s*$");

    private final UpdateNotificationService updateNotificationService;

    public DefaultRaucService(
            UpdateNotificationService updateNotificationService
    ) {
        this.updateNotificationService = updateNotificationService;
    }

    @Override
    public String getCurrentSlot() {
        try {
            return getBootedSlot(getStatus());
        } catch (Exception e) {
            logger.error("Could not read current RAUC slot", e);
        }

        return null;
    }

    @Override
    public String getStatus() throws Exception {
        ShellManager shellManager = new ShellManager(
                new String[]{"rauc", "status"}, false
        );

        Process process = shellManager.getProcess();

        // Read full output
        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        process.waitFor();
        shellManager.close();

        logger.debug("RAUC status: {}", output);

        return output;
    }

    // The slot the system booted from, e.g. "rootfs.0", read from the output of "rauc status". Public for the self-test.
    public static String getBootedSlot(String raucStatus) {
        for (String line : raucStatus.split("\\R")) {
            line = line.trim();
            if (line.startsWith("Booted from:")) {
                // Extract first token after colon
                String value = line.substring("Booted from:".length()).trim();
                return value.split("\\s+")[0]; // e.g. "rootfs.0"
            }
        }

        return null;
    }

    // The progress in a line of "rauc install", e.g. " 40% Checking bundle done.", or null for any other line
    // Package visible for the tests
    static UpdateState getProgress(String line) {
        Matcher matcher = PROGRESS_PATTERN.matcher(line);

        if (!matcher.matches()) {
            return null;
        }

        UpdateState updateState = new UpdateState();
        updateState.setProgressPercentage(Integer.parseInt(matcher.group(1)));
        updateState.setProgressMessage(matcher.group(2).trim());
        return updateState;
    }

    // Why "rauc install" failed, for the web app: the last error RAUC reported (e.g. "LastError: Failed verifying
    // signature: ..."), otherwise the last line it wrote (e.g. when its service didn't run) or its exit code
    // Package visible for the tests
    static String getFailureReason(List<String> output, int exitCode) {
        String lastLine = null;

        for (int i = output.size() - 1; i >= 0; i--) {
            String line = output.get(i).trim();

            if (line.startsWith("LastError:")) {
                return line.substring("LastError:".length()).trim();
            }

            if (lastLine == null && !line.isEmpty() && !line.equals("idle") && !(line.startsWith("Installing `") && line.endsWith("failed")) && getProgress(line) == null) {
                lastLine = line;
            }
        }

        return lastLine == null ? "RAUC exited with exit code " + exitCode : lastLine;
    }

    @Override
    public void installBundle(String url) throws Exception {
        ShellManager shellManager = new ShellManager(
                new String[]{"rauc", "install", url}, false
        );

        Process process = shellManager.getProcess();

        // The last lines, to tell why it failed
        List<String> output = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("RAUC output: {}", line);

                output.add(line);
                if (output.size() > 50) {
                    output.removeFirst();
                }

                UpdateState updateState = getProgress(line);
                if (updateState != null) {
                    updateNotificationService.notifyClients(updateState);
                }
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception(getFailureReason(output, exitCode));
        }
    }

    @Override
    public void markGood() throws InterruptedException, IOException {
        ShellManager shellManager = new ShellManager(new String[]{"rauc", "status", "mark-good"});
        shellManager.getProcess().waitFor();
        shellManager.close();
    }

}
