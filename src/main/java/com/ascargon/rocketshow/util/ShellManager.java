package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ShellManager {

    private final static Logger logger = LoggerFactory.getLogger(ShellManager.class);

    private Process process;
    private PrintStream outStream;

    public ShellManager(String[] command) throws IOException {
        logger.debug("Execute shell command: " + String.join(" ", command));

        process = new ProcessBuilder(command).redirectErrorStream(true).start();
        outStream = new PrintStream(process.getOutputStream());

        if (logger.isDebugEnabled()) {
            // log the output from the call
            logInputStreamAsync(process.getInputStream());
        }
    }

    public static void logInputStreamAsync(InputStream inputStream) {
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

            logger.info("Shell command output:\n{}", sb.toString());
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

    public Process getProcess() {
        return process;
    }

}
