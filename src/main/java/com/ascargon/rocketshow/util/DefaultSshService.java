package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.RocketShowApplication;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultSshService implements SshService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSshService.class);
    private final static String PASSWORD_CHARACTERS = "abcdefghijklmnopqrstuvwxyz";
    private final static int PASSWORD_LENGTH = 8;
    private final static long DISABLE_DELAY_HOURS = 1;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> disableSshFuture;

    @Override
    public synchronized String enableSsh() throws IOException, InterruptedException {
        String password = generatePassword();

        runScript("sudo", new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "enable-ssh.sh", password);
        runScript("sudo", "nft", "add", "element", "inet", "filter", "ola_open", "{", "9090", "}");
        scheduleDisableSsh();

        return password;
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(secureRandom.nextInt(PASSWORD_CHARACTERS.length())));
        }

        return password.toString();
    }

    private void scheduleDisableSsh() {
        if (disableSshFuture != null) {
            disableSshFuture.cancel(false);
        }

        disableSshFuture = scheduler.schedule(this::disableSsh, DISABLE_DELAY_HOURS, TimeUnit.HOURS);
    }

    private void disableSsh() {
        try {
            runScript("sudo", new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "disable-ssh.sh");
        } catch (Exception e) {
            logger.error("Could not disable SSH", e);
        } finally {
            synchronized (this) {
                disableSshFuture = null;
            }
        }
    }

    private void runScript(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        try (var inputStream = process.getInputStream()) {
            inputStream.transferTo(OutputStream.nullOutputStream());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + command[0]);
        }
    }

    @PreDestroy
    public void destroy() {
        if (disableSshFuture != null) {
            disableSshFuture.cancel(false);
        }

        scheduler.shutdownNow();
    }

}
