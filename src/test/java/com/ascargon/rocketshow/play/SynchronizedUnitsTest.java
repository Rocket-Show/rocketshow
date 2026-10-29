package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.api.RemoteDevice;
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
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Playing on several units: the units synchronized with this one load, play and stop the same compositions.
 */
class SynchronizedUnitsTest {

    @TempDir
    Path directory;

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private SettingsService settingsService;
    private DefaultPlayerService playerService;

    @BeforeEach
    void setUp() throws Exception {
        // The other units
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestHeaders().getFirst("X-API-Key") + " " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        settingsService = TestSettings.inDirectory(directory);
        String host = "127.0.0.1:" + server.getAddress().getPort();
        ObjectMapper objectMapper = new ObjectMapper();
        settingsService.getSettings().getRemoteDeviceList().add(objectMapper.readValue("{\"name\": \"Stage right\", \"host\": \"" + host + "\", \"synchronize\": true, \"apiKey\": \"right\"}", RemoteDevice.class));
        settingsService.getSettings().getRemoteDeviceList().add(objectMapper.readValue("{\"name\": \"Foyer\", \"host\": \"" + host + "\", \"synchronize\": false, \"apiKey\": \"foyer\"}", RemoteDevice.class));

        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.getSession()).thenReturn(new Session());

        DefaultCompositionService compositionService = new DefaultCompositionService(settingsService, capabilitiesService, mock(GstDiscovererService.class), mock(DesignerService.class));
        Composition composition = new Composition();
        composition.setName("Song 1 & 2");
        ActionTriggerComposition later = new ActionTriggerComposition();
        later.setPositionMillis(600_000);
        composition.getActionTriggerList().add(later);
        compositionService.saveComposition(composition);

        playerService = new DefaultPlayerService(mock(NotificationService.class), settingsService, compositionService, new DefaultSetService(), sessionService,
                mock(LightingService.class), mock(DesignerService.class), mock(MidiTimecodeService.class), mock(ActionExecutionService.class),
                capabilitiesService, mock(CompositionPipelineBuilder.class), mock(RaspberryGpioOutService.class), mock(GstreamerInitService.class));

        // Selecting the first composition at the start stops the other units as well
        assertEquals(List.of("right /api/transport/stop?playDefaultComposition=true"), requestsAfter(500));
        requests.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        playerService.close();
        server.stop(0);
    }

    // The requests arrive from other threads
    private List<String> requestsAfter(long millis) throws InterruptedException {
        Thread.sleep(millis);
        return List.copyOf(requests);
    }

    @Test
    void loadsOnTheSynchronizedUnitsBeforePlaying() throws Exception {
        playerService.play();

        assertEquals(List.of(
                "right /api/transport/load?name=Song+1+%26+2",
                "right /api/transport/play"
        ), requestsAfter(500));
        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());
    }

    @Test
    void stopsAndPausesTheSynchronizedUnits() throws Exception {
        playerService.play();
        requestsAfter(500);
        requests.clear();

        playerService.pause();
        assertEquals(List.of("right /api/transport/pause"), requestsAfter(500));
        requests.clear();

        playerService.stop(false);
        assertEquals(List.of("right /api/transport/stop?playDefaultComposition=false"), requestsAfter(500));
    }

    @Test
    void playsSamplesOnTheSynchronizedUnits() throws Exception {
        playerService.playAsSample("Song 1 & 2");

        assertEquals(List.of("right /api/transport/play-as-sample?name=Song+1+%26+2"), requestsAfter(500));
    }

}
