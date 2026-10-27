package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
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
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stepping through a set or all compositions, like the buttons, MIDI and GPIO triggers do during a show.
 * Nothing is played here.
 */
class PlayerSetTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final Session session = new Session();
    private final DefaultSetService setService = new DefaultSetService();
    private DefaultCompositionService compositionService;
    private final DesignerService designerService = mock(DesignerService.class);
    private DefaultPlayerService playerService;

    @BeforeEach
    void setUp() throws Exception {
        settingsService = TestSettings.inDirectory(directory);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());
        when(sessionService.getSession()).thenReturn(session);

        compositionService = new DefaultCompositionService(settingsService, capabilitiesService, mock(GstDiscovererService.class));

        for (String name : new String[]{"D", "B", "A", "C"}) {
            Composition composition = new Composition();
            composition.setName(name);
            compositionService.saveComposition(composition);
        }

        compositionService.saveSet(set("Show", "C", "A", "D"));
        compositionService.saveSet(set("Empty"));

        playerService = start();
    }

    // Like a start of the unit, which loads the set of the last session
    private DefaultPlayerService start() {
        return new DefaultPlayerService(mock(NotificationService.class), settingsService, compositionService, setService, sessionService,
                mock(LightingService.class), designerService, mock(MidiTimecodeService.class), mock(ActionExecutionService.class),
                capabilitiesService, mock(CompositionPipelineBuilder.class), mock(RaspberryGpioOutService.class), mock(GstreamerInitService.class));
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

    // The compositions selected by pressing a button again and again
    private List<String> press(Runnable button, int times) {
        List<String> selected = new ArrayList<>();

        for (int i = 0; i < times; i++) {
            button.run();
            selected.add(playerService.getCompositionName());
        }

        return selected;
    }

    private void next() {
        try {
            playerService.setNextComposition();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void previous() {
        try {
            playerService.setPreviousComposition();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void startsWithTheFirstCompositionWithoutSet() {
        assertNull(setService.getCurrentSet());
        assertEquals("A", playerService.getCompositionName());
    }

    @Test
    void startsWithTheSetOfTheLastSession() {
        session.setCurrentSetName("Show");

        playerService = start();

        assertEquals("Show", setService.getCurrentSet().getName());
        assertEquals("C", playerService.getCompositionName());
    }

    @Test
    void remembersTheLoadedSet() throws Exception {
        playerService.loadSetAndComposition("Show");

        assertEquals("C", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());
        assertEquals("Show", session.getCurrentSetName());
        verify(sessionService, atLeastOnce()).save();
    }

    @Test
    void stepsThroughTheSetWithoutWrappingAround() throws Exception {
        playerService.loadSetAndComposition("Show");

        assertEquals(List.of("A", "D", "D"), press(this::next, 3));
        assertEquals(2, setService.getCurrentCompositionIndex());

        assertEquals(List.of("A", "C", "C"), press(this::previous, 3));
        assertEquals(0, setService.getCurrentCompositionIndex());
    }

    @Test
    void continuesTheSetFromACompositionSelectedByName() throws Exception {
        playerService.loadSetAndComposition("Show");

        // E.g. selected by a MIDI trigger
        playerService.setCompositionName("D");
        assertEquals(2, setService.getCurrentCompositionIndex());
        assertEquals(List.of("A", "C"), press(this::previous, 2));

        // A composition outside the set leaves the position in the set
        playerService.setCompositionName("B");
        assertEquals("B", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());
    }

    @Test
    void keepsThePositionOfACompositionInTheSetTwice() throws Exception {
        compositionService.saveSet(set("Reprise", "A", "B", "A"));
        playerService.loadSetAndComposition("Reprise");

        assertEquals(List.of("B", "A"), press(this::next, 2));
        playerService.setCompositionName("A");

        assertEquals(2, setService.getCurrentCompositionIndex());
    }

    @Test
    void stepsThroughAllCompositionsWithoutSet() throws Exception {
        playerService.loadSetAndComposition("");

        assertEquals(List.of("B", "C", "D", "D"), press(this::next, 4));
        assertEquals(List.of("C", "B", "A", "A"), press(this::previous, 4));
    }

    @Test
    void selectsNothingInAnEmptySet() throws Exception {
        playerService.loadSetAndComposition("Empty");

        assertNull(playerService.getCompositionName());
        assertEquals(Arrays.asList(null, null), press(this::next, 2));
    }

    @Test
    void usesAllCompositionsForASetWhichIsGone() throws Exception {
        playerService.loadSetAndComposition("Deleted set");

        assertNull(setService.getCurrentSet());
        assertEquals("A", playerService.getCompositionName());
    }

    @Test
    void selectsTheNextCompositionOfTheSetWhenOneHasFinished() throws Exception {
        playerService.loadSetAndComposition("Show");

        session.setAutoSelectNextComposition(false);
        playerService.compositionPlayerFinishedPlaying(playerService.getCurrentCompositionPlayer());
        assertEquals("C", playerService.getCompositionName());

        session.setAutoSelectNextComposition(true);
        playerService.compositionPlayerFinishedPlaying(playerService.getCurrentCompositionPlayer());
        assertEquals("A", playerService.getCompositionName());
        assertEquals(1, setService.getCurrentCompositionIndex());
    }


    @Test
    void startsTheNextCompositionOfTheSetWithoutTheDefaultComposition() throws Exception {
        // Compositions of lighting actions, e.g. a loop of the stage lighting between the songs
        for (String name : new String[]{"Ambient", "C", "A"}) {
            Composition composition = new Composition();
            composition.setName(name);
            ActionTriggerComposition later = new ActionTriggerComposition();
            later.setPositionMillis(600_000);
            composition.getActionTriggerList().add(later);
            compositionService.saveComposition(composition);
        }
        settingsService.getSettings().setDefaultComposition("Ambient");

        Set show = set("Auto", "C", "A");
        show.getSetCompositionList().getFirst().setAutoStartNextComposition(true);
        compositionService.saveSet(show);
        session.setAutoSelectNextComposition(true);

        playerService = start();
        playerService.loadSetAndComposition("Auto");
        playerService.play();
        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        clearInvocations(designerService);

        // C reached its end
        playerService.compositionPlayerFinishedPlaying(playerService.getCurrentCompositionPlayer());

        assertEquals("A", playerService.getCompositionName());
        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
        verify(designerService, never()).getProjectByCompositionName("Ambient");
    }


    @Test
    void stopsAndContinuesWithoutTheDefaultComposition() throws Exception {
        // The default composition was deleted or renamed
        settingsService.getSettings().setDefaultComposition("Deleted");
        Composition song = new Composition();
        song.setName("C");
        ActionTriggerComposition later = new ActionTriggerComposition();
        later.setPositionMillis(600_000);
        song.getActionTriggerList().add(later);
        compositionService.saveComposition(song);
        session.setAutoSelectNextComposition(true);

        playerService = start();
        playerService.loadSetAndComposition("Show");
        playerService.play();

        // C reached its end, the next composition gets selected
        playerService.compositionPlayerFinishedPlaying(playerService.getCurrentCompositionPlayer());
        assertEquals("A", playerService.getCompositionName());

        playerService.stop();
    }


    @Test
    void playsTheDefaultCompositionAgainOnOtherAudioDevices() throws Exception {
        // Without default composition
        playerService.restartDefaultComposition();

        Composition ambient = new Composition();
        ambient.setName("Ambient");
        ActionTriggerComposition later = new ActionTriggerComposition();
        later.setPositionMillis(600_000);
        ambient.getActionTriggerList().add(later);
        compositionService.saveComposition(ambient);
        settingsService.getSettings().setDefaultComposition("Ambient");

        playerService = start();
        verify(designerService, atLeastOnce()).getProjectByCompositionName("Ambient");
        clearInvocations(designerService);

        playerService.restartDefaultComposition();

        verify(designerService, atLeastOnce()).getProjectByCompositionName("Ambient");
        playerService.stop(false);
    }


    @Test
    void stopsAndResumesTheDefaultCompositionForTheSelfTest() throws Exception {
        saveCompositionWithoutFiles("Ambient", 600_000);
        settingsService.getSettings().setDefaultComposition("Ambient");
        playerService = start();

        // The default composition plays between the shows
        assertFalse(playerService.isPlayingComposition());

        playerService.stopDefaultComposition();
        clearInvocations(designerService);
        playerService.resumeDefaultComposition();
        verify(designerService, atLeastOnce()).getProjectByCompositionName("Ambient");

        // Not while a composition plays
        saveCompositionWithoutFiles("C", 600_000);
        playerService.loadSetAndComposition("Show");
        playerService.play();
        assertTrue(playerService.isPlayingComposition());

        clearInvocations(designerService);
        playerService.resumeDefaultComposition();
        verify(designerService, never()).getProjectByCompositionName("Ambient");

        playerService.stop(false);
        assertFalse(playerService.isPlayingComposition());
    }

    // A composition without files, e.g. only lighting, which ends with its last action
    private void saveCompositionWithoutFiles(String name, long lastActionMillis) throws Exception {
        Composition composition = new Composition();
        composition.setName(name);
        ActionTriggerComposition lastAction = new ActionTriggerComposition();
        lastAction.setPositionMillis(lastActionMillis);
        composition.getActionTriggerList().add(lastAction);
        compositionService.saveComposition(composition);
    }

    private void waitFor(String compositionName, CompositionPlayer.PlayState playState) throws InterruptedException {
        for (int i = 0; i < 100 && !(compositionName.equals(playerService.getCompositionName()) && playState == playerService.getPlayState()); i++) {
            Thread.sleep(50);
        }

        assertEquals(compositionName, playerService.getCompositionName());
        assertEquals(playState, playerService.getPlayState());
    }

    @Test
    void selectsTheNextCompositionWhenOneWithoutFilesEnds() throws Exception {
        saveCompositionWithoutFiles("C", 100);
        session.setAutoSelectNextComposition(true);
        playerService.loadSetAndComposition("Show");

        playerService.play();

        waitFor("A", CompositionPlayer.PlayState.STOPPED);
        assertEquals(1, setService.getCurrentCompositionIndex());
    }

    @Test
    void startsTheNextCompositionWithoutFiles() throws Exception {
        saveCompositionWithoutFiles("C", 100);
        saveCompositionWithoutFiles("A", 600_000);
        Set show = set("Auto", "C", "A");
        show.getSetCompositionList().getFirst().setAutoStartNextComposition(true);
        compositionService.saveSet(show);
        session.setAutoSelectNextComposition(true);
        playerService.loadSetAndComposition("Auto");

        playerService.play();

        waitFor("A", CompositionPlayer.PlayState.PLAYING);
        playerService.stop();
    }

    @Test
    void keepsTheSetWhenTheDefaultCompositionEnds() throws Exception {
        // A default composition which doesn't loop
        saveCompositionWithoutFiles("Ambient", 100);
        settingsService.getSettings().setDefaultComposition("Ambient");
        session.setAutoSelectNextComposition(true);
        session.setCurrentSetName("Show");

        playerService = start();
        Thread.sleep(500);

        assertEquals("C", playerService.getCompositionName());
        assertEquals(0, setService.getCurrentCompositionIndex());
        assertEquals(CompositionPlayer.PlayState.STOPPED, playerService.getPlayState());
    }

}
