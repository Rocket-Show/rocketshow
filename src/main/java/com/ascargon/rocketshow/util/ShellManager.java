package com.ascargon.rocketshow.util;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ShellManager {

    private final static Logger logger = LoggerFactory.getLogger(ShellManager.class);

    @Getter
    private final Process process;
    private final PrintStream outStream;

    public ShellManager(String[] command) throws IOException {
        logger.debug("Execute shell command: {}", String.join(" ", command));

        process = new ProcessBuilder(command).redirectErrorStream(true).start();
        outStream = new PrintStream(process.getOutputStream());

        if (logger.isDebugEnabled()) {
            // log the output from the call
            logInputStreamAsync(process.getInputStream(), command);
        }
    }

    public static void logInputStreamAsync(InputStream inputStream, String[] command) {
        Runnable task = () -> {
            StringBuilder sb = new StringBuilder();
            String line;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                logger.error("Error reading shell command output", e);
            }

            logger.info("Shell command output:\n{}\n\nfor command {}", sb, command);
        };

        Thread thread = new Thread(task);
        thread.start();
    }

    public void sendCommand(String command, boolean newLine) {
        if (newLine) {
            outStream.println(command);
        } else {
            outStream.print(command);
        }
        outStream.flush();
    }

    public void close() {
        if (process != null) {
            process.destroy();
        }
    }

}