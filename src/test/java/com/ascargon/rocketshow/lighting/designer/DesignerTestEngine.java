package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plays a designer project like the app does and returns the DMX values the engine sends.
 */
class DesignerTestEngine {

    // The errors logged while playing
    final List<String> errors = new CopyOnWriteArrayList<>();

    private static class ErrorCollector extends AbstractAppender {

        private final List<String> errors;

        ErrorCollector(List<String> errors) {
            super("DesignerTestEngine", null, null, true, Property.EMPTY_ARRAY);
            this.errors = errors;
        }

        @Override
        public void append(LogEvent event) {
            // Only the lighting: other tests leave apps running, e.g. trying to open the MIDI devices of their settings
            if (event.getLevel().isMoreSpecificThan(Level.ERROR) && event.getLoggerName().startsWith("com.ascargon.rocketshow.lighting")) {
                errors.add(event.getMessage().getFormattedMessage() + (event.getThrown() == null ? "" : ": " + event.getThrown()));
            }
        }

    }

    /**
     * Play the composition of the project at the passed position and return the first DMX values the engine sends,
     * by universe and DMX address (1 to 512).
     */
    Map<String, Map<Integer, Integer>> play(Path directory, String project, String compositionName, long positionMillis, List<String> universeUuids) throws Exception {
        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        composition.setName(compositionName);
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPositionMillis()).thenReturn(positionMillis);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);

        return run(directory, project, universeUuids, (designerService, loadedProject) -> {
            designerService.load(compositionPlayer, loadedProject, null);
            designerService.play();
        });
    }

    /**
     * Preview the project like the designer of the web app does, while editing it, and return the first DMX values
     * the engine sends.
     */
    Map<String, Map<Integer, Integer>> preview(Path directory, String project, java.util.function.Consumer<DefaultDesignerService> select, List<String> universeUuids) throws Exception {
        return run(directory, project, universeUuids, (designerService, loadedProject) -> {
            // Like DesignerController.preview
            designerService.stopPreview();
            designerService.load(null, loadedProject, null);
            select.accept(designerService);
            designerService.startPreview(0);
        });
    }

    private Map<String, Map<Integer, Integer>> run(Path directory, String project, List<String> universeUuids, java.util.function.BiConsumer<DefaultDesignerService, Project> start) throws Exception {
        Path designerPath = Files.createDirectories(directory.resolve("designer"));
        try (var files = Files.list(designerPath)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.writeString(designerPath.resolve("Project.json"), project);

        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);
        for (String universeUuid : universeUuids) {
            LightingUniverse lightingUniverse = new LightingUniverse();
            lightingUniverse.setUuid(universeUuid);
            settings.getLightingUniverseList().add(lightingUniverse);
        }

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        LightingService lightingService = mock(LightingService.class);
        List<LightingUniverseState> lightingUniverses = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> lightingUniverses.add(invocation.getArgument(0))).when(lightingService).addLightingUniverse(any());
        doAnswer(invocation -> lightingUniverses.remove((LightingUniverseState) invocation.getArgument(0))).when(lightingService).removeLightingUniverse(any());

        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<Map<String, Map<Integer, Integer>>> universes = new AtomicReference<>();
        doAnswer(invocation -> {
            if (universes.get() == null) {
                Map<String, Map<Integer, Integer>> values = new HashMap<>();
                for (LightingUniverseState lightingUniverse : lightingUniverses) {
                    Map<Integer, Integer> addresses = new HashMap<>();
                    lightingUniverse.getUniverse().forEach((channel, value) -> addresses.put(channel + 1, value));
                    values.put(lightingUniverse.getMappingUuid(), addresses);
                }
                universes.set(values);
                sent.countDown();
            }
            return null;
        }).when(lightingService).sendExternalSync(anyBoolean());

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        ErrorCollector errorCollector = new ErrorCollector(errors);
        errorCollector.start();
        context.getConfiguration().getRootLogger().addAppender(errorCollector, null, null);
        context.updateLoggers();

        try {
            DefaultDesignerService designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), lightingService, new DefaultFixturePixelGroupResolveService());
            start.accept(designerService, designerService.getAllProjects().getFirst());

            try {
                assertTrue(sent.await(10, TimeUnit.SECONDS), "The engine did not send the universe");
            } finally {
                designerService.close();
            }
        } finally {
            context.getConfiguration().getRootLogger().removeAppender(errorCollector.getName());
            context.updateLoggers();
            errorCollector.stop();
        }

        return universes.get();
    }

}
