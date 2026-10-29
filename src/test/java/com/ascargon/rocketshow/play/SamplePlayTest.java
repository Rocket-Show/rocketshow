package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.DefaultCompositionService;
import com.ascargon.rocketshow.composition.DefaultSetService;
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
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.ActionTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Compositions played as samples, e.g. from the pads of a MIDI controller, many times during a show.
 */
class SamplePlayTest {

    @TempDir
    Path directory;

    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private DefaultPlayerService playerService;

    @BeforeEach
    void setUp() throws Exception {
        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.getSession()).thenReturn(new Session());

        DefaultCompositionService compositionService = new DefaultCompositionService(TestSettings.inDirectory(directory), capabilitiesService, mock(GstDiscovererService.class), mock(DesignerService.class));

        // A short composition triggering an action, e.g. a light flash
        Composition composition = new Composition();
        composition.setName("Flash");
        ActionTriggerComposition actionTrigger = new ActionTriggerComposition();
        actionTrigger.setPositionMillis(20);
        composition.getActionTriggerList().add(actionTrigger);
        compositionService.saveComposition(composition);

        playerService = new DefaultPlayerService(mock(NotificationService.class), TestSettings.inDirectory(directory), compositionService, new DefaultSetService(),
                sessionService, mock(LightingService.class), mock(DesignerService.class), mock(MidiTimecodeService.class), actionExecutionService,
                capabilitiesService, mock(CompositionPipelineBuilder.class), mock(RaspberryGpioOutService.class), mock(GstreamerInitService.class));
    }

    @Test
    void playsASampleAgainAndAgain() throws Exception {
        for (int i = 1; i <= 30; i++) {
            playerService.playAsSample("Flash");

            verify(actionExecutionService, timeout(2000).times(i)).executeFromTrigger(any(ActionTrigger.class));

            // Let the sample end before the next pad is hit
            Thread.sleep(150);
        }

        verify(actionExecutionService, times(30)).executeFromTrigger(any(ActionTrigger.class));
    }

}
