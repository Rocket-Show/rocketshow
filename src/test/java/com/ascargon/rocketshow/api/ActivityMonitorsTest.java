package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.DefaultActivityNotificationAudioService;
import com.ascargon.rocketshow.lighting.DefaultActivityNotificationLightingService;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.midi.DefaultActivityNotificationMidiService;
import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The monitors of the web app: audio levels, MIDI and DMX activity, sent over websockets.
 */
class ActivityMonitorsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);

    // The messages a web app gets
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final WebSocketSession webApp = mock(WebSocketSession.class);

    @BeforeEach
    void setUp() throws Exception {
        settings.setEnableMonitor(true);
        when(settingsService.getSettings()).thenReturn(settings);
        when(webApp.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            received.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(webApp).sendMessage(any());
    }

    private JsonNode lastReceived() {
        return objectMapper.readTree(received.getLast());
    }

    private static LightingUniverseState universe(int value) {
        LightingUniverseState universe = new LightingUniverseState();
        universe.getUniverse().put(0, value);
        return universe;
    }

    @Test
    void showsTheLatestDmxValues() throws Exception {
        DefaultActivityNotificationLightingService monitor = new DefaultActivityNotificationLightingService();
        monitor.afterConnectionEstablished(webApp);

        // A fade ending in a blackout, faster than the monitor sends
        for (int value = 255; value >= 0; value -= 15) {
            monitor.notifyClients(universe(value));
        }
        Thread.sleep(300);

        assertEquals(0, lastReceived().get("universe").get("0").asInt());
    }

    @Test
    void sendsTheAudioLevelsByBus() throws Exception {
        AudioBus stage = new AudioBus();
        stage.setName("Stage");
        stage.setChannels(2);
        AudioBus monitorBus = new AudioBus();
        monitorBus.setName("In ear");
        monitorBus.setChannels(1);
        settings.getAudioBusList().clear();
        settings.getAudioBusList().addAll(List.of(stage, monitorBus));

        DefaultActivityNotificationAudioService monitor = new DefaultActivityNotificationAudioService(settingsService);
        monitor.afterConnectionEstablished(webApp);

        monitor.notifyClients(new double[]{-10, -20, -30, -40});
        Thread.sleep(300);

        JsonNode buses = lastReceived().get("activityAudioBusList");
        assertEquals(2, buses.size());
        assertEquals("Stage", buses.get(0).get("name").asString());
        assertEquals(List.of(-10.0, -20.0), levels(buses.get(0)));
        assertEquals("In ear", buses.get(1).get("name").asString());
        assertEquals(List.of(-30.0), levels(buses.get(1)));
    }

    private static List<Double> levels(JsonNode bus) {
        List<Double> levels = new ArrayList<>();
        bus.get("activityAudioChannelList").forEach(channel -> levels.add(channel.get("volumeDb").asDouble()));
        return levels;
    }

    @Test
    void showsTheLatestAudioLevels() throws Exception {
        AudioBus stage = new AudioBus();
        stage.setChannels(1);
        settings.getAudioBusList().clear();
        settings.getAudioBusList().add(stage);

        DefaultActivityNotificationAudioService monitor = new DefaultActivityNotificationAudioService(settingsService);
        monitor.afterConnectionEstablished(webApp);

        // Level messages of the pipelines, faster than the web app takes them
        doAnswer(invocation -> {
            Thread.sleep(5);
            received.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(webApp).sendMessage(any());
        int threadsBefore = Thread.activeCount();
        int mostThreads = threadsBefore;
        for (int i = 0; i < 500; i++) {
            monitor.notifyClients(new double[]{-i});
            mostThreads = Math.max(mostThreads, Thread.activeCount());
        }
        Thread.sleep(1000);

        assertEquals(List.of(-499.0), levels(lastReceived().get("activityAudioBusList").get(0)));
        assertTrue(mostThreads - threadsBefore < 10, (mostThreads - threadsBefore) + " threads to send the levels");
    }

    @Test
    void sendsNoAudioLevelsWithoutAudioBus() throws Exception {
        settings.getAudioBusList().clear();
        DefaultActivityNotificationAudioService monitor = new DefaultActivityNotificationAudioService(settingsService);
        monitor.afterConnectionEstablished(webApp);

        monitor.notifyClients(new double[]{-10});
        Thread.sleep(300);

        assertEquals(0, lastReceivedOrEmpty().size());
    }

    private JsonNode lastReceivedOrEmpty() {
        return received.isEmpty() ? objectMapper.createObjectNode() : lastReceived();
    }

    @Test
    void routesMidiWhileTheMonitorSends() throws Exception {
        DefaultActivityNotificationMidiService monitor = new DefaultActivityNotificationMidiService(settingsService);
        monitor.afterConnectionEstablished(webApp);
        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);

        // MIDI from several inputs at once, e.g. a keyboard and a MIDI file, for a second
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService inputs = Executors.newFixedThreadPool(4);
        long end = System.currentTimeMillis() + 1000;
        for (int input = 0; input < 4; input++) {
            int number = input;
            inputs.execute(() -> {
                while (System.currentTimeMillis() < end) {
                    try {
                        monitor.notifyClients(noteOn, number % 2 == 0 ? MidiDirection.IN : MidiDirection.OUT, MidiSource.values()[number % MidiSource.values().length], MidiDestination.values()[number % MidiDestination.values().length]);
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }
            });
        }
        inputs.shutdown();
        assertTrue(inputs.awaitTermination(10, TimeUnit.SECONDS));
        Thread.sleep(300);

        assertEquals(List.of(), failures.stream().map(Throwable::toString).distinct().toList());
        assertTrue(received.size() > 5, received.size() + " MIDI activities sent");
    }

}
