package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.ActionHttp;
import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.lighting.ActionLighting;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.midi.ActionMidi;
import com.ascargon.rocketshow.midi.MidiRouter;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.midi.MidiSource;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The actions of triggers (MIDI, GPIO, compositions, the scheduler) and the ones other units ask for.
 */
class DefaultActionExecutionServiceTest {

    private final PlayerService playerService = mock(PlayerService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final RebootService rebootService = mock(RebootService.class);
    private final LightingService lightingService = mock(LightingService.class);
    private final MidiRouter midiRouter = mock(MidiRouter.class);
    private final RaspberryGpioOutService raspberryGpioOutService = mock(RaspberryGpioOutService.class);

    private DefaultActionExecutionService actionExecutionService;

    @BeforeEach
    void setUp() {
        MidiRouterFactory midiRouterFactory = mock(MidiRouterFactory.class);
        when(midiRouterFactory.getSettingsMidiRouter(any())).thenReturn(midiRouter);

        actionExecutionService = new DefaultActionExecutionService(playerService, settingsService, rebootService, lightingService, midiRouterFactory, raspberryGpioOutService);
    }

    private static ActionTransport transport(ActionTransport.TransportActionType type, String compositionName) {
        ActionTransport action = new ActionTransport();
        action.setTransportActionType(type);
        action.setCompositionName(compositionName);
        return action;
    }

    private static ActionHttp http(ActionHttp.HttpMethod method, String url, String body) {
        ActionHttp action = new ActionHttp();
        action.setHttpMethod(method);
        action.setUrl(url);
        action.setBody(body);
        return action;
    }

    private static ActionTriggerComposition trigger(Action... actions) {
        ActionTriggerComposition trigger = new ActionTriggerComposition();
        trigger.getActionList().addAll(List.of(actions));
        return trigger;
    }

    @Test
    void controlsThePlayer() throws Exception {
        actionExecutionService.executeFromTrigger(trigger(
                transport(ActionTransport.TransportActionType.PLAY, null),
                transport(ActionTransport.TransportActionType.PAUSE, null),
                transport(ActionTransport.TransportActionType.TOGGLE_PLAY, null),
                transport(ActionTransport.TransportActionType.STOP, null),
                transport(ActionTransport.TransportActionType.NEXT_COMPOSITION, null),
                transport(ActionTransport.TransportActionType.PREVIOUS_COMPOSITION, null),
                transport(ActionTransport.TransportActionType.SELECT_COMPOSITION_BY_NAME, "Song 1"),
                transport(ActionTransport.TransportActionType.SELECT_COMPOSITION_BY_NAME_AND_PLAY, "Song 2"),
                transport(ActionTransport.TransportActionType.PLAY_AS_SAMPLE, "Applause")));

        var inOrder = inOrder(playerService);
        inOrder.verify(playerService).play();
        inOrder.verify(playerService).pause();
        inOrder.verify(playerService).togglePlay();
        inOrder.verify(playerService).stop();
        inOrder.verify(playerService).setNextComposition();
        inOrder.verify(playerService).setPreviousComposition();
        inOrder.verify(playerService).setCompositionName("Song 1");
        inOrder.verify(playerService).setCompositionName("Song 2");
        inOrder.verify(playerService).play();
        inOrder.verify(playerService).playAsSample("Applause");
    }

    @Test
    void sendsMidiSignals() throws Exception {
        ActionMidi action = new ActionMidi();
        action.setMidiSignal(new MidiSignal(ShortMessage.PROGRAM_CHANGE, 3, 12, 0));

        actionExecutionService.execute(action);

        verify(midiRouter).sendSignal(argThat(message -> message instanceof ShortMessage shortMessage
                && shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE && shortMessage.getChannel() == 3 && shortMessage.getData1() == 12), eq(MidiSource.ACTION));
    }

    @Test
    void handsLightingGpioAndRebootToTheirServices() throws Exception {
        ActionLighting lighting = new ActionLighting();
        ActionRaspberryGpio gpio = new ActionRaspberryGpio();
        gpio.setPinId(17);
        gpio.setHigh(true);
        ActionSystem reboot = new ActionSystem();
        reboot.setSystemActionType(ActionSystem.SystemActionType.REBOOT);

        actionExecutionService.executeFromTrigger(trigger(lighting, gpio, reboot));

        verify(lightingService).executeAction(lighting);
        verify(raspberryGpioOutService).executeAction(gpio);
        verify(rebootService).reboot();
    }

    @Test
    void asksOtherUnitsToExecuteTheirPart() throws Exception {
        RemoteDevice unit2 = mock(RemoteDevice.class);
        when(settingsService.getRemoteDeviceByName("Unit 2")).thenReturn(unit2);

        ActionTransport play = transport(ActionTransport.TransportActionType.PLAY, null);
        play.setExecuteLocally(false);
        play.getRemoteDeviceNames().addAll(List.of("Unit 2", "Unit which is gone"));

        actionExecutionService.execute(play);

        verify(unit2).doPost("system/execute-action", play);
        verify(playerService, never()).play();
    }

    @Test
    void sendsHttpRequests() throws Exception {
        BlockingQueue<String> requests = new LinkedBlockingQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Token") + " " + body);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            ActionHttp post = http(ActionHttp.HttpMethod.POST, url + "/scene?id=2", "{\"on\":true}");
            post.getHeaderList().put("X-Token", "secret");

            actionExecutionService.executeFromTrigger(trigger(post, http(ActionHttp.HttpMethod.GET, url + "/state", null),
                    http(ActionHttp.HttpMethod.DELETE, url + "/scene", null)));

            List<String> received = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                received.add(requests.poll(5, TimeUnit.SECONDS));
            }

            assertEquals(List.of("POST /scene?id=2 secret {\"on\":true}", "GET /state null ", "DELETE /scene null "), received);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotWaitForAServerWhichDoesNotAnswer() throws Exception {
        try (ServerSocket hangingServer = new ServerSocket(0)) {
            List<Socket> connections = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        connections.add(hangingServer.accept());
                    }
                } catch (IOException e) {
                    // closed
                }
            });
            acceptor.start();

            ActionMidi midi = new ActionMidi();
            midi.setMidiSignal(new MidiSignal(ShortMessage.NOTE_ON, 0, 60, 100));

            long start = System.currentTimeMillis();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    actionExecutionService.executeFromTrigger(trigger(http(ActionHttp.HttpMethod.GET, "http://127.0.0.1:" + hangingServer.getLocalPort() + "/", null), midi)));
            long millis = System.currentTimeMillis() - start;

            // The MIDI signal after the request goes out right away
            assertTrue(millis < 1000, "Waited " + millis + " ms");
            verify(midiRouter, timeout(1000)).sendSignal(any(), eq(MidiSource.ACTION));
        }
    }

    @Test
    void executesNothingForAnEmptyTrigger() throws Exception {
        actionExecutionService.executeFromTrigger(trigger());

        verifyNoInteractions(playerService, rebootService, lightingService, raspberryGpioOutService);
    }

}
