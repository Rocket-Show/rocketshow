package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mixing the DMX values of everything playing (compositions, the designer, MIDI) into the universes sent to OLA.
 */
class DefaultLightingServiceTest {

    private final Settings settings = new Settings();
    private final ActivityNotificationLightingService activityNotificationLightingService = mock(ActivityNotificationLightingService.class);

    // The mixed values of all universes, as sent to the monitor of the web app
    private final BlockingQueue<HashMap<Integer, Integer>> sentUniverses = new LinkedBlockingQueue<>();

    private DefaultLightingService lightingService;

    @BeforeEach
    void setUp() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        settings.setLightingSendDelayMillis(10);
        settings.setEnableMonitor(true);

        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(new OperatingSystemInformation());

        doAnswer(invocation -> {
            LightingUniverseState activityUniverse = invocation.getArgument(0);
            sentUniverses.add(new HashMap<>(activityUniverse.getUniverse()));
            return null;
        }).when(activityNotificationLightingService).notifyClients(any());

        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());

        lightingService = new DefaultLightingService(capabilitiesService, activityNotificationLightingService, settingsService, operatingSystemInformationService);
    }

    @AfterEach
    void tearDown() {
        lightingService.close();
    }

    private static LightingUniverseState state(String name, String mappingUuid, int channel, int value) {
        LightingUniverseState lightingUniverseState = new LightingUniverseState();
        lightingUniverseState.setName(name);
        lightingUniverseState.setMappingUuid(mappingUuid);
        lightingUniverseState.getUniverse().put(channel, value);
        return lightingUniverseState;
    }

    private static LightingUniverse universe(String name) {
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setName(name);
        return lightingUniverse;
    }

    private HashMap<Integer, Integer> nextSentUniverse() throws InterruptedException {
        HashMap<Integer, Integer> universe = sentUniverses.poll(5, TimeUnit.SECONDS);
        assertNotNull(universe, "Nothing sent");
        return universe;
    }

    @Test
    void takesTheHighestValueOfEachChannel() {
        LightingUniverse stage = universe("Stage");
        LightingUniverseState composition = state(null, null, 0, 100);
        composition.getUniverse().put(1, 255);
        LightingUniverseState designer = state(null, null, 0, 200);
        designer.getUniverse().put(1, 10);

        short[] mixed = lightingService.mixLightingUniverses(List.of(composition, designer), stage, true);

        assertEquals(512, mixed.length);
        assertEquals(200, mixed[0]);
        assertEquals(255, mixed[1]);
        assertEquals(0, mixed[2]);
    }

    @Test
    void sendsEverythingToTheOnlyUniverse() {
        LightingUniverse stage = universe("Stage");

        short[] mixed = lightingService.mixLightingUniverses(List.of(state("Other", null, 0, 50)), stage, true);

        assertEquals(50, mixed[0]);
    }

    @Test
    void sendsToTheUniverseByMappingThenName() {
        LightingUniverse stage = universe("Stage");
        LightingUniverse front = universe("Front");
        settings.getLightingUniverseList().addAll(List.of(stage, front));

        List<LightingUniverseState> states = List.of(
                state("Front", stage.getUuid(), 0, 10),  // the mapping wins over the name
                state("Front", null, 1, 20),
                state(null, null, 2, 30),                // no name: the first universe
                state("Back", null, 3, 40)               // no such universe
        );

        short[] mixedStage = lightingService.mixLightingUniverses(states, stage, false);
        short[] mixedFront = lightingService.mixLightingUniverses(states, front, false);

        assertEquals(List.of(10, 0, 30, 0), List.of((int) mixedStage[0], (int) mixedStage[1], (int) mixedStage[2], (int) mixedStage[3]));
        assertEquals(List.of(0, 20, 0, 0), List.of((int) mixedFront[0], (int) mixedFront[1], (int) mixedFront[2], (int) mixedFront[3]));
    }

    @Test
    void holdsTheValuesOfALightingAction() throws Exception {
        LightingUniverse stage = universe("Stage");
        settings.getLightingUniverseList().add(stage);

        ActionLighting action = new ActionLighting();
        action.getLightingActionUniverseList().add(actionUniverse(stage.getUuid(), 5, 200));
        action.getLightingActionUniverseList().add(actionUniverse("deleted universe", 6, 100));

        lightingService.executeAction(action);

        HashMap<Integer, Integer> sent = nextSentUniverse();
        assertEquals(200, sent.get(5));
        assertEquals(0, sent.get(6));
    }

    private static LightingActionUniverse actionUniverse(String universeUuid, int channel, int value) {
        LightingActionChannelValue channelValue = new LightingActionChannelValue();
        channelValue.setChannel(channel);
        channelValue.setValue(value);

        LightingActionUniverse actionUniverse = new LightingActionUniverse();
        actionUniverse.setUniverseUuid(universeUuid);
        actionUniverse.getChannelValueList().add(channelValue);
        return actionUniverse;
    }

    @Test
    void sendsTheChangedValues() throws Exception {
        LightingUniverseState composition = state(null, null, 0, 100);
        lightingService.addLightingUniverse(composition);

        lightingService.send();
        assertEquals(100, nextSentUniverse().get(0));

        composition.getUniverse().put(0, 0);
        lightingService.send();
        assertEquals(0, nextSentUniverse().get(0));
    }

    @Test
    void sendsAValueSetWhileSending() throws Exception {
        LightingUniverseState composition = state(null, null, 0, 100);
        lightingService.addLightingUniverse(composition);

        // While the first value is being sent, the last one of a fade arrives. Counted apart from the sent universes,
        // which the test takes out meanwhile.
        AtomicInteger sendCount = new AtomicInteger();
        doAnswer(invocation -> {
            LightingUniverseState activityUniverse = invocation.getArgument(0);
            sentUniverses.add(new HashMap<>(activityUniverse.getUniverse()));

            if (sendCount.incrementAndGet() == 1) {
                composition.getUniverse().put(0, 0);
                lightingService.send();
            }
            return null;
        }).when(activityNotificationLightingService).notifyClients(any());

        lightingService.send();

        assertEquals(100, nextSentUniverse().get(0));
        assertEquals(0, nextSentUniverse().get(0));
    }

}
