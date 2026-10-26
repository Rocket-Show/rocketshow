package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Project;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole app with the data of the latest release sample (src/test/resources/compat/&lt;release&gt;/),
 * like a unit after an update. Every setting is set and every list filled, e.g. GPIO, TLS, remote devices and
 * the designer live preview are enabled.
 */
class ReleaseSampleStartTest extends ApiTest {

    private static Path directory;

    // Version numbers of the samples, e.g. 3.0.0 < 3.0.10
    private static Comparator<Path> byVersion() {
        return Comparator.comparing(path -> Stream.of(path.getFileName().toString().split("\\."))
                .map(part -> String.format("%010d", Integer.parseInt(part.replaceAll("\\D", "0"))))
                .reduce("", String::concat));
    }

    @DynamicPropertySource
    static void useTheLatestReleaseSample(DynamicPropertyRegistry registry) throws IOException, URISyntaxException {
        Path compatPath = Path.of(ReleaseSampleStartTest.class.getResource("/compat").toURI());
        Path latestSample;

        try (Stream<Path> samples = Files.list(compatPath)) {
            latestSample = samples.filter(Files::isDirectory).max(byVersion()).orElseThrow();
        }

        directory = Files.createTempDirectory("rocketshow-release-sample");
        FileUtils.copyDirectory(latestSample.toFile(), directory.toFile());

        // The sample has generic paths: put the data into the copy of the sample
        Path settingsFile = directory.resolve("settings.xml");
        String settings = Files.readString(settingsFile, StandardCharsets.UTF_8)
                .replaceFirst("<basePath>[^<]*</basePath>", "<basePath>" + directory + File.separator + "</basePath>")
                .replaceFirst("<designerPath>[^<]*</designerPath>", "<designerPath>designer</designerPath>");
        Files.writeString(settingsFile, settings, StandardCharsets.UTF_8);

        registry.add("rocketshow.directory", directory::toString);
    }

    @AfterAll
    static void deleteTheCopy() throws IOException {
        FileUtils.deleteDirectory(directory.toFile());
    }

    @Autowired
    CompositionService compositionService;

    @Autowired
    DesignerService designerService;

    @Test
    void loadsTheData() {
        assertEquals(directory + File.separator, settingsService.getSettings().getBasePath());
        assertTrue(settingsService.getSettings().getEnableRaspberryGpio());

        assertEquals("Sample composition", compositionService.getAllCompositions().stream().map(Composition::getName).findFirst().orElse(null));
        assertEquals("Sample set", compositionService.getAllSets().stream().map(Set::getName).findFirst().orElse(null));
        assertEquals("Sample project", designerService.getAllProjects().stream().map(Project::getName).findFirst().orElse(null));
    }

    @Test
    void sendsTheDataToTheWebApp() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/system/settings").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"deviceName\":\"deviceName-")));
        mockMvc.perform(get("/api/composition/list").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample composition")));
        mockMvc.perform(get("/api/composition").param("name", "Sample composition").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample composition")));
        mockMvc.perform(get("/api/set/list").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample set")));
        mockMvc.perform(get("/api/projects").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample project")));
        mockMvc.perform(get("/api/system/state").session(admin))
                .andExpect(status().isOk());
    }

}
