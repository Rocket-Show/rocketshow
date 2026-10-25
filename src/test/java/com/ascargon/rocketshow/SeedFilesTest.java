package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seed files a new unit starts with (dist/): the example compositions and the designer template. They only need
 * to work with the latest release, so they are tested as they are instead of keeping them per release. The designer
 * template is also played in DesignerProjectCompatibilityTest.
 */
class SeedFilesTest {

    private final static Path COMPOSITIONS_PATH = Path.of("dist", "defaults", "compositions");
    private final static Path DESIGNER_TEMPLATE = Path.of("dist", "designer_template.json");

    private List<Path> getCompositionFiles() throws Exception {
        try (Stream<Path> paths = Files.list(COMPOSITIONS_PATH)) {
            return paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }
    }

    @TestFactory
    Stream<DynamicTest> loadsTheExampleCompositions() throws Exception {
        List<Path> compositionFiles = getCompositionFiles();
        assertFalse(compositionFiles.isEmpty());

        return compositionFiles.stream().map(file -> DynamicTest.dynamicTest(file.toString(), () -> {
            List<String> problems = StrictXml.getProblems(file, Composition.class);
            assertTrue(problems.isEmpty(), file + " is not understood by the current version: " + problems);
        }));
    }

    @Test
    void designerTemplateUsesTheExampleCompositions() throws Exception {
        // A composition is named like its file
        List<String> compositionNames = getCompositionFiles().stream()
                .map(path -> path.getFileName().toString().replaceAll("\\.xml$", ""))
                .toList();

        for (JsonNode composition : new ObjectMapper().readTree(DESIGNER_TEMPLATE.toFile()).get("compositions")) {
            String name = composition.get("name").asString();
            assertTrue(compositionNames.contains(name), "The designer template uses the composition '" + name + "', which is not in " + COMPOSITIONS_PATH);
        }
    }

}
