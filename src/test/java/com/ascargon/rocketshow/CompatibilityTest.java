package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.update.UpdateState;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Loads the files written by earlier releases (src/test/resources/compat/&lt;release&gt;/, see
 * {@link ReleaseSampleGenerator}) with the current classes. An element the current version does not understand fails
 * the test, because its content would be lost silently on the next save.
 */
class CompatibilityTest {

    private Class<?> getType(Path relativePath) {
        String fileName = relativePath.getFileName().toString();
        String directory = relativePath.getNameCount() > 2 ? relativePath.getName(1).toString() : "";

        if (directory.equals("compositions")) {
            return Composition.class;
        } else if (directory.equals("sets")) {
            return com.ascargon.rocketshow.composition.Set.class;
        } else if (fileName.equals("settings.xml")) {
            return Settings.class;
        } else if (fileName.equals("session.xml")) {
            return Session.class;
        } else if (fileName.equals("update.xml")) {
            return UpdateState.class;
        }

        return null;
    }

    @TestFactory
    Stream<DynamicTest> loadsTheFilesOfEarlierReleases() throws Exception {
        Path compatPath = Path.of(getClass().getResource("/compat").toURI());

        List<Path> files;
        try (Stream<Path> paths = Files.walk(compatPath)) {
            // Designer projects are played in DesignerProjectCompatibilityTest
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getParent().getFileName().toString().equals("designer"))
                    .sorted()
                    .toList();
        }

        assertFalse(files.isEmpty());

        return files.stream().map(file -> {
            Path relativePath = compatPath.relativize(file);

            return DynamicTest.dynamicTest(relativePath.toString(), () -> {
                Class<?> type = getType(relativePath);

                if (type == null) {
                    fail(relativePath + " is an unknown file. Add its type to the test.");
                }

                List<String> problems = StrictXml.getProblems(file, type);
                assertTrue(problems.isEmpty(), relativePath + " is not understood by the current version: " + problems);
            });
        });
    }

}
