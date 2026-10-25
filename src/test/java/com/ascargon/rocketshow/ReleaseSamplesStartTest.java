package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.SettingsUpdateSystemService;
import com.ascargon.rocketshow.update.UpdateService;
import com.ascargon.rocketshow.util.FactoryResetService;
import com.ascargon.rocketshow.util.RebootService;
import com.ascargon.rocketshow.util.ShutdownService;
import com.ascargon.rocketshow.util.SshService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * Starts the whole app with the data of each release sample (src/test/resources/compat/&lt;release&gt;/), like a unit
 * updated from that release. Every setting is set and every list filled. The API with the latest sample is tested in
 * ReleaseSampleStartTest.
 */
class ReleaseSamplesStartTest {

    // Saving the settings writes system files and runs system commands. Never really run the others.
    private final static List<Class<?>> SYSTEM_SERVICES = List.of(SettingsUpdateSystemService.class, RebootService.class, ShutdownService.class, FactoryResetService.class, SshService.class, UpdateService.class);

    @TempDir
    Path tempPath;

    // Replaces the system services with mocks
    private static class SystemServiceMocks implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

            for (Class type : SYSTEM_SERVICES) {
                for (String name : beanFactory.getBeanNamesForType(type, true, false)) {
                    registry.removeBeanDefinition(name);
                    registry.registerBeanDefinition(name, BeanDefinitionBuilder.genericBeanDefinition(type, () -> mock(type)).getBeanDefinition());
                }
            }
        }

    }

    private ConfigurableApplicationContext start(Path sample) throws Exception {
        Path directory = tempPath.resolve(sample.getFileName().toString());
        FileUtils.copyDirectory(sample.toFile(), directory.toFile());

        // The sample has generic paths: put the data into the copy of the sample
        Path settingsFile = directory.resolve("settings.xml");
        String settings = Files.readString(settingsFile, StandardCharsets.UTF_8)
                .replaceFirst("<basePath>[^<]*</basePath>", "<basePath>" + directory + File.separator + "</basePath>")
                .replaceFirst("<designerPath>[^<]*</designerPath>", "<designerPath>designer</designerPath>");
        Files.writeString(settingsFile, settings, StandardCharsets.UTF_8);

        return new SpringApplicationBuilder(RocketShowApplication.class)
                .initializers(context -> context.addBeanFactoryPostProcessor(new SystemServiceMocks()))
                .properties("rocketshow.directory=" + directory, "server.port=0")
                .run();
    }

    @TestFactory
    Stream<DynamicTest> startsWithTheDataOfEachRelease() throws Exception {
        Path compatPath = Path.of(getClass().getResource("/compat").toURI());

        List<Path> samples;
        try (Stream<Path> paths = Files.list(compatPath)) {
            samples = paths.filter(Files::isDirectory).sorted().toList();
        }

        return samples.stream().map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(), () -> {
            try (ConfigurableApplicationContext context = start(sample)) {
                for (Class<?> type : SYSTEM_SERVICES) {
                    assertTrue(mockingDetails(context.getBean(type)).isMock(), type.getSimpleName() + " is not a mock");
                }

                assertEquals(tempPath.resolve(sample.getFileName().toString()) + File.separator, context.getBean(SettingsService.class).getSettings().getBasePath());
                assertEquals(List.of("Sample composition"), context.getBean(CompositionService.class).getAllCompositions().stream().map(Composition::getName).toList());
                assertEquals(List.of("Sample set"), context.getBean(CompositionService.class).getAllSets().stream().map(Set::getName).toList());
                assertEquals(List.of("Sample project"), context.getBean(DesignerService.class).getAllProjects().stream().map(Project::getName).toList());
            }
        }));
    }

}
