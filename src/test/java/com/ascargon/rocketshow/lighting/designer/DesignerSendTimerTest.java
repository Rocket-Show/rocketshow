package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The timer of the designer engine sending the universes while a composition plays.
 */
class DesignerSendTimerTest {

    @TempDir
    Path basePath;

    @Test
    void sendsNothingAfterClosing() throws Exception {
        Files.createDirectories(basePath.resolve("designer"));
        Files.copy(Path.of("dist", "designer_template.json"), basePath.resolve("designer/Template.json"));

        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setEnableMonitor(false);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        FileFilterService fileFilterService = mock(FileFilterService.class);
        when(fileFilterService.filterFile(anyString())).thenReturn(false);

        // A send taking a while (e.g. to OLA), which is running when the composition stops
        LightingService lightingService = mock(LightingService.class);
        AtomicBoolean sending = new AtomicBoolean();
        CountDownLatch sendStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.set(true);
            sendStarted.countDown();
            Thread.sleep(200);
            sending.set(false);
            return null;
        }).when(lightingService).sendExternalSync(anyBoolean());

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, fileFilterService, lightingService, new DefaultFixturePixelGroupResolveService());
        Project project = designerService.getAllProjects().getFirst();
        for (Fixture fixture : project.getFixtures()) {
            LightingUniverse lightingUniverse = new LightingUniverse();
            lightingUniverse.setUuid(fixture.getDmxUniverseUuid());
            settings.getLightingUniverseList().add(lightingUniverse);
        }

        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        Composition designerComposition = project.getCompositions()[0];
        composition.setName(designerComposition.getName());
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);

        designerService.load(compositionPlayer, project, null);
        designerService.play();
        assertTrue(sendStarted.await(5, TimeUnit.SECONDS));

        designerService.close();

        assertFalse(sending.get(), "Still sending after closing");
    }

}
