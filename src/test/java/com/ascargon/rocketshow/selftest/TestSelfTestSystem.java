package com.ascargon.rocketshow.selftest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The system of a unit for the tests: its files in a directory of the test, the output of its commands and its
 * environment given by the test.
 */
class TestSelfTestSystem extends SelfTestSystem {

    private final Path root;
    private final Map<String, CommandResult> commands = new ConcurrentHashMap<>();
    final Map<String, String> environment = new ConcurrentHashMap<>();
    final Map<String, List<String>> addresses = new ConcurrentHashMap<>();

    TestSelfTestSystem(Path root) {
        super(root);
        this.root = root;
    }

    void file(String path, String content) throws IOException {
        file(path, content.getBytes());
    }

    void file(String path, byte[] content) throws IOException {
        Path file = root.resolve(path.substring(1));
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    void directory(String path) throws IOException {
        Files.createDirectories(root.resolve(path.substring(1)));
    }

    void command(String output, String... command) {
        command(0, output, command);
    }

    void command(int exitCode, String output, String... command) {
        commands.put(String.join(" ", command), new CommandResult(exitCode, output));
    }

    // A command which isn't installed
    @Override
    CommandResult run(String... command) throws Exception {
        CommandResult result = commands.get(String.join(" ", command));
        if (result == null) {
            throw new IOException("Cannot run program \"" + command[0] + "\": No such file or directory");
        }
        return result;
    }

    @Override
    Optional<String> getenv(String name) {
        return Optional.ofNullable(environment.get(name));
    }

    @Override
    List<String> getAddresses(String networkInterface) {
        return addresses.getOrDefault(networkInterface, List.of());
    }

}
