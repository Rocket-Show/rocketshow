package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.CompositionController;
import com.ascargon.rocketshow.api.ControllerService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.DefaultCompositionService;
import com.ascargon.rocketshow.composition.DefaultSetService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.composition.SetComposition;
import com.ascargon.rocketshow.gstreamer.GstDiscovererService;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saving and deleting compositions in the web app during a show, e.g. fixing the next song while one plays.
 */
class ChangeCompositionsWhilePlayingTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
    private final GstDiscovererService gstDiscovererService = mock(GstDiscovererService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final DefaultSetService setService = new DefaultSetService();
    private DefaultCompositionService compositionService;
    private DefaultPlayerService playerService;
    private CompositionController compositionController;

    @BeforeEach
    void setUp() throws Exception {
        settingsService = TestSettings.inDirectory(directory);
        Capabilities capabilities = new Capabilities();
        capabilities.setGstreamer(true);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);
        when(sessionService.getSession()).thenReturn(new Session());
        when(gstDiscovererService.getDurationMillis(any())).thenReturn(90_000L);
        when(gstDiscovererService.getChannels(any())).thenReturn(2);

        compositionService = new DefaultCompositionService(settingsService, capabilitiesService, gstDiscovererService);

        for (String name : new String[]{"A", "B", "C", "D"}) {
            compositionService.saveComposition(composition(name, 600_000));
        }

        compositionService.saveSet(set("Show", "C", "A", "D"));

        playerService = new DefaultPlayerService(mock(NotificationService.class), settingsService, compositionService, setService, sessionService,
                mock(LightingService.class), mock(DesignerService.class), mock(MidiTimecodeService.class), mock(ActionExecutionService.class),
                capabilitiesService, mock(CompositionPipelineBuilder.class), mock(RaspberryGpioOutService.class), mock(GstreamerInitService.class));
        compositionController = new CompositionController(mock(ControllerService.class), compositionService, playerService, mock(DesignerService.class), settingsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        playerService.stop();
    }

    // A composition without files, which plays until its last action (e.g. lighting)
    private static Composition composition(String name, long lastActionMillis) {
        Composition composition = new Composition();
        composition.setName(name);
        ActionTriggerComposition action = new ActionTriggerComposition();
        action.setPositionMillis(lastActionMillis);
        composition.getActionTriggerList().add(action);
        return composition;
    }

    private static Set set(String name, String... compositionNames) {
        Set set = new Set();
        set.setName(name);

        for (String compositionName : compositionNames) {
            SetComposition setComposition = new SetComposition();
            setComposition.setName(compositionName);
            set.getSetCompositionList().add(setComposition);
        }

        return set;
    }

    @Test
    void keepsPlayingWhenAnotherCompositionIsSaved() throws Exception {
        playerService.loadSetAndComposition("Show");
        playerService.play();

        compositionController.save(composition("A", 300_000), null);

        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        assertEquals("C", playerService.getCompositionName());

        playerService.stop();
        playerService.setNextComposition();
        assertEquals("A", playerService.getCompositionName());
        assertEquals(300_000, playerService.getCompositionDurationMillis());
    }

    @Test
    void keepsThePositionInTheSet() throws Exception {
        playerService.loadSetAndComposition("Show");
        playerService.setNextComposition();

        compositionController.save(composition("D", 300_000), null);

        assertEquals("A", playerService.getCompositionName());
        assertEquals(1, setService.getCurrentCompositionIndex());
        assertEquals(300_000, setService.getCurrentSet().getSetCompositionList().get(2).getDurationMillis());
    }

    @Test
    void takesTheSavedCompositionPlayingOnceItStops() throws Exception {
        playerService.loadSetAndComposition("Show");
        playerService.play();

        compositionController.save(composition("C", 300_000), null);

        // Plays on as it was
        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        assertEquals(600_000, playerService.getCompositionDurationMillis());

        playerService.stop();
        assertEquals(300_000, playerService.getCompositionDurationMillis());
    }

    @Test
    void takesTheSavedCompositionAtOnceWhileStopped() throws Exception {
        playerService.loadSetAndComposition("Show");

        compositionController.save(composition("C", 300_000), null);

        assertEquals("C", playerService.getCompositionName());
        assertEquals(300_000, playerService.getCompositionDurationMillis());
    }

    @Test
    void keepsPlayingWhenAnotherCompositionIsDeleted() throws Exception {
        playerService.loadSetAndComposition("Show");
        playerService.setNextComposition();
        playerService.play();

        compositionController.delete("C");

        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        assertEquals("A", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());
        playerService.setNextComposition();
        assertEquals("D", playerService.getCompositionName());
    }

    @Test
    void keepsThePositionOfACompositionInTheSetTwice() throws Exception {
        compositionService.saveSet(set("Reprise", "A", "B", "A"));
        playerService.loadSetAndComposition("Reprise");
        playerService.setNextComposition();
        playerService.setNextComposition();

        compositionController.delete("B");

        assertEquals("A", playerService.getCompositionName());
        assertEquals(1, setService.getCurrentCompositionIndex());
    }

    @Test
    void selectsTheCompositionTakingThePlaceOfTheDeletedOne() throws Exception {
        playerService.loadSetAndComposition("Show");
        playerService.setNextComposition();

        compositionController.delete("A");
        assertEquals("D", playerService.getCompositionName());
        assertEquals(1, setService.getCurrentCompositionIndex());

        // The last one of the set
        compositionController.delete("D");
        assertEquals("C", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());

        compositionController.delete("C");
        assertNull(playerService.getCompositionName());
    }

    @Test
    void selectsTheFirstCompositionWhenTheCurrentOneIsDeletedWithoutSet() throws Exception {
        playerService.loadSetAndComposition("");
        playerService.setNextComposition();

        compositionController.delete("B");
        assertEquals("A", playerService.getCompositionName());

        for (String name : new String[]{"A", "C", "D"}) {
            compositionController.delete(name);
        }
        assertNull(playerService.getCompositionName());

        // Nothing selected
        compositionController.save(composition("E", 600_000), null);
        compositionController.delete("E");
        assertNull(playerService.getCompositionName());
    }

    @Test
    void analyzesOnlyNewOrChangedFiles() throws Exception {
        Settings settings = settingsService.getSettings();
        File audioFile = new File(settings.getBasePath() + settings.getMediaPath() + File.separator + settings.getAudioPath() + File.separator + "song.wav");
        Files.createDirectories(audioFile.getParentFile().toPath());
        Files.writeString(audioFile.toPath(), "audio");
        audioFile.setLastModified(System.currentTimeMillis() - 10_000);

        Composition song = composition("Song", 0);
        AudioCompositionFile audioCompositionFile = new AudioCompositionFile();
        audioCompositionFile.setName("song.wav");
        song.getCompositionFileList().add(audioCompositionFile);

        compositionController.save(song, null);
        verify(gstDiscovererService, times(1)).getDiscovererInformation(anyString());
        assertEquals(90_000, compositionService.getComposition("Song").getDurationMillis());

        // E.g. a changed lighting action: the file isn't analyzed again during the show
        compositionController.save(song, null);
        verify(gstDiscovererService, times(1)).getDiscovererInformation(anyString());
        assertEquals(90_000, compositionService.getComposition("Song").getDurationMillis());

        // Uploaded again
        audioFile.setLastModified(System.currentTimeMillis() + 10_000);
        when(gstDiscovererService.getDurationMillis(any())).thenReturn(120_000L);
        compositionController.save(song, null);
        verify(gstDiscovererService, times(2)).getDiscovererInformation(anyString());
        assertEquals(120_000, compositionService.getComposition("Song").getDurationMillis());
    }

}
