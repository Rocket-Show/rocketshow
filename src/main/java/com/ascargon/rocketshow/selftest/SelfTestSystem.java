package com.ascargon.rocketshow.selftest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The files and commands of the system the checks read, e.g. /proc/mounts or vcgencmd. The tests give their own.
 */
@Service
class SelfTestSystem {

    private static final long COMMAND_TIMEOUT_SECONDS = 10;

    record CommandResult(int exitCode, String output) {
    }

    // "/" on a unit
    private final Path root;

    @Autowired
    SelfTestSystem() {
        this(Path.of("/"));
    }

    SelfTestSystem(Path root) {
        this.root = root;
    }

    private Path resolve(String path) {
        return root.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    // The content of a file, empty if it's missing or can't be read
    Optional<String> read(String path) {
        try {
            return Optional.of(Files.readString(resolve(path), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    Optional<byte[]> readBytes(String path) {
        try {
            return Optional.of(Files.readAllBytes(resolve(path)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    // The names in a directory, sorted, empty if it's missing
    List<String> list(String directory) {
        try (Stream<Path> paths = Files.list(resolve(directory))) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    // The IPv4 addresses of a network interface, e.g. eth0
    List<String> getAddresses(String networkInterface) {
        try {
            NetworkInterface found = NetworkInterface.getByName(networkInterface);
            if (found == null) {
                return List.of();
            }
            return found.inetAddresses().filter(address -> address instanceof Inet4Address).map(address -> address.getHostAddress()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    Optional<String> getenv(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    // Runs a command with its output and error output together. Throws when it can't run or takes too long.
    CommandResult run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });

        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException(String.join(" ", command) + " took longer than " + COMMAND_TIMEOUT_SECONDS + " s");
        }

        return new CommandResult(process.exitValue(), output.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS).trim());
    }

}
