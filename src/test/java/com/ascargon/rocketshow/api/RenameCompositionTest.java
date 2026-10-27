package com.ascargon.rocketshow.api;

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
import com.ascargon.rocketshow.lighting.designer.DefaultDesignerService;
import com.ascargon.rocketshow.lighting.designer.DefaultFixturePixelGroupResolveService;
import com.ascargon.rocketshow.midi.ActionTriggerMidi;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.play.DefaultPlayerService;
import com.ascargon.rocketshow.raspberry.ActionTriggerRaspberryGpio;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.scheduler.ScheduledComposition;
import com.ascargon.rocketshow.session.Session;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.ActionTransport;
import com.ascargon.rocketshow.util.ActionTrigger;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renaming a composition in the editor of the web app: everything using it keeps it under its new name.
 */
class RenameCompositionTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
    private final GstDiscovererService gstDiscovererService = mock(GstDiscovererService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final DefaultSetService setService = new DefaultSetService();
    private DefaultCompositionService compositionService;
    private DefaultDesignerService designerService;
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
        designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
        playerService = new DefaultPlayerService(mock(NotificationService.class), settingsService, compositionService, setService, sessionService,
                mock(LightingService.class), designerService, mock(MidiTimecodeService.class), mock(ActionExecutionService.class),
                capabilitiesService, mock(CompositionPipelineBuilder.class), mock(RaspberryGpioOutService.class), mock(GstreamerInitService.class));
        compositionController = new CompositionController(mock(ControllerService.class), compositionService, playerService, designerService, settingsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        playerService.stop();
        designerService.close();
    }

    private static ActionTransport select(String compositionName) {
        ActionTransport actionTransport = new ActionTransport();
        actionTransport.setTransportActionType(ActionTransport.TransportActionType.SELECT_COMPOSITION_BY_NAME);
        actionTransport.setCompositionName(compositionName);
        return actionTransport;
    }

    // With an audio file and a lighting action far out, so it plays until stopped
    private Composition intro(String name) throws Exception {
        Settings settings = settingsService.getSettings();
        File audioFile = new File(settings.getBasePath() + settings.getMediaPath() + File.separator + settings.getAudioPath() + File.separator + "intro.wav");
        Files.createDirectories(audioFile.getParentFile().toPath());
        if (!audioFile.exists()) {
            Files.writeString(audioFile.toPath(), "audio");
            audioFile.setLastModified(System.currentTimeMillis() - 10_000);
        }

        Composition composition = new Composition();
        composition.setName(name);
        AudioCompositionFile audioCompositionFile = new AudioCompositionFile();
        audioCompositionFile.setName("intro.wav");
        composition.getCompositionFileList().add(audioCompositionFile);
        ActionTriggerComposition lighting = new ActionTriggerComposition();
        lighting.setPositionMillis(600_000);
        composition.getActionTriggerList().add(lighting);
        return composition;
    }

    // Everything using the intro: a set, another composition, a designer project, the settings
    private void useTheIntro() throws Exception {
        compositionService.saveComposition(intro("Intro"));

        Composition song = new Composition();
        song.setName("Song");
        ActionTriggerComposition selectIntro = new ActionTriggerComposition();
        selectIntro.getActionList().add(select("Intro"));
        song.getActionTriggerList().add(selectIntro);
        compositionService.saveComposition(song);

        Set show = new Set();
        show.setName("Show");
        for (String name : new String[]{"Intro", "Song", "Intro"}) {
            SetComposition setComposition = new SetComposition();
            setComposition.setName(name);
            show.getSetCompositionList().add(setComposition);
        }
        compositionService.saveSet(show);

        designerService.saveProject("""
                {"name": "Lights", "version": 8, "presets": [], "scenes": [], "fixtures": [],
                 "compositions": [{"name": "Intro", "durationMillis": 90000, "scenePlaybackRegions": [], "fromTheFuture": "kept"}],
                 "notReadByTheApp": {"kept": true}}
                """);

        Settings settings = settingsService.getSettings();
        settings.setDefaultComposition("Intro");
        ScheduledComposition scheduledComposition = new ScheduledComposition();
        scheduledComposition.setCompositionName("Intro");
        settings.getScheduledCompositionList().add(scheduledComposition);
        ActionTriggerMidi midiTrigger = new ActionTriggerMidi();
        midiTrigger.getActionList().add(select("Intro"));
        settings.getActionTriggerMidiList().add(midiTrigger);
        ActionTriggerRaspberryGpio gpioTrigger = new ActionTriggerRaspberryGpio();
        gpioTrigger.getActionList().add(select("Intro"));
        settings.getActionTriggerRaspberryGpioList().add(gpioTrigger);
        settingsService.save();
    }

    private static String selectedComposition(List<? extends ActionTrigger> actionTriggers) {
        return ((ActionTransport) actionTriggers.getFirst().getActionList().getFirst()).getCompositionName();
    }

    @Test
    void keepsTheCompositionEverywhereUnderItsNewName() throws Exception {
        useTheIntro();
        playerService.loadSetAndComposition("Show");

        compositionController.save(intro("Opening"), "Intro");

        // Read again from the files, like after a restart
        DefaultCompositionService restarted = new DefaultCompositionService(settingsService, capabilitiesService, gstDiscovererService);
        assertNull(restarted.getComposition("Intro"));
        assertNotNull(restarted.getComposition("Opening"));
        assertFalse(directory.resolve("compositions/Intro.xml").toFile().exists());
        assertEquals(List.of("Opening", "Song", "Opening"), restarted.getSet("Show").getSetCompositionList().stream().map(SetComposition::getName).toList());
        assertEquals("Opening", selectedComposition(restarted.getComposition("Song").getActionTriggerList()));

        JsonNode project = new ObjectMapper().readTree(directory.resolve("designer/Lights.json").toFile());
        assertEquals("Opening", project.get("compositions").get(0).get("name").stringValue());
        assertEquals("kept", project.get("compositions").get(0).get("fromTheFuture").stringValue());
        assertEquals(true, project.get("notReadByTheApp").get("kept").booleanValue());
        assertNotNull(designerService.getProjectByCompositionName("Opening"));

        Settings settings = TestSettings.inDirectory(directory).getSettings();
        assertEquals("Opening", settings.getDefaultComposition());
        assertEquals("Opening", settings.getScheduledCompositionList().getFirst().getCompositionName());
        assertEquals("Opening", selectedComposition(settings.getActionTriggerMidiList()));
        assertEquals("Opening", selectedComposition(settings.getActionTriggerRaspberryGpioList()));

        // Still selected, at the same position of the set
        assertEquals("Opening", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());

        // Its files aren't analyzed again
        verify(gstDiscovererService, times(1)).getDiscovererInformation(anyString());
    }

    @Test
    void keepsPlayingTheRenamedComposition() throws Exception {
        useTheIntro();
        playerService.loadSetAndComposition("Show");
        playerService.play();

        compositionController.save(intro("Opening"), "Intro");

        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        assertEquals("Opening", playerService.getCompositionName());

        playerService.stop();
        assertEquals("Opening", playerService.getCompositionName());
        playerService.setNextComposition();
        assertEquals("Song", playerService.getCompositionName());
    }

}
