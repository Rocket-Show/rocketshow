package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.OlaPort;
import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The DMX check of the self-test, with OLA mocked: what it sends to the universes of the settings.
 */
class DmxSelfTestTest {

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final Capabilities capabilities = new Capabilities();
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
    private final LightingService lightingService = mock(LightingService.class);
    private final OlaService olaService = mock(OlaService.class);

    // The values each universe got, by OLA universe
    private final Map<Integer, List<short[]>> sent = new ConcurrentHashMap<>();

    private DmxSelfTest dmxSelfTest;

    @BeforeEach
    void setUp() {
        capabilities.setOla(true);
        when(settingsService.getSettings()).thenReturn(settings);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);
        when(lightingService.isPatched(any())).thenReturn(true);
        when(lightingService.sendDmx(any(), any())).thenAnswer(invocation -> {
            LightingUniverse lightingUniverse = invocation.getArgument(0);
            sent.computeIfAbsent(lightingUniverse.getOlaUniverseId(), id -> new CopyOnWriteArrayList<>()).add(invocation.getArgument(1));
            return true;
        });

        dmxSelfTest = new DmxSelfTest(settingsService, capabilitiesService, lightingService, olaService);
        dmxSelfTest.rampMillis = 400;
    }

    private LightingUniverse universe(String name, Integer olaUniverseId, String portId) {
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setName(name);
        lightingUniverse.setOlaUniverseId(olaUniverseId);
        lightingUniverse.setOlaOutputPortId(portId);
        settings.getLightingUniverseList().add(lightingUniverse);
        return lightingUniverse;
    }

    private SelfTestContext runSelfTest() throws Exception {
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> false);
        dmxSelfTest.prepare();
        try {
            dmxSelfTest.run(context);
        } finally {
            dmxSelfTest.restore();
        }
        return context;
    }

    // The checks of the ramp, without the one of the interface
    private static List<SelfTestCheck> ramp(SelfTestContext context) {
        return context.getChecks().stream().filter(check -> check.getName().equals(DmxSelfTest.DMX)).toList();
    }

    @Test
    void sendsARampOnEveryChannelThenZero() throws Exception {
        universe("Stage", 1, "3-O-0");
        universe("Front", 2, "4-O-0");

        SelfTestContext context = runSelfTest();

        assertEquals(List.of("Stage, OLA universe 1 on port 3-O-0", "Front, OLA universe 2 on port 4-O-0"), ramp(context).stream().map(SelfTestCheck::getTarget).toList());
        for (SelfTestCheck check : ramp(context)) {
            assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
            assertEquals("Ramp from 0 to 255 on 512 channels sent in 0.4 s", check.getDetails());
        }

        for (List<short[]> universe : sent.values()) {
            assertTrue(universe.size() > 5, universe.size() + " frames");
            // Every channel the same, rising to full, then zero
            int previous = -1;
            for (short[] values : universe.subList(0, universe.size() - 1)) {
                assertEquals(512, values.length);
                for (short value : values) {
                    assertEquals(values[0], value);
                }
                assertTrue(values[0] >= previous, "Not rising");
                previous = values[0];
            }
            assertEquals(255, previous);
            assertEquals(0, universe.getLast()[0]);
            assertEquals(0, universe.getLast()[511]);
        }

        // Then the values of the unit again
        verify(lightingService).send();
    }

    @Test
    void failsForAUniverseOlaHasNotPatched() throws Exception {
        LightingUniverse stage = universe("Stage", 1, "3-O-0");
        universe("Front", 2, "4-O-0");
        when(lightingService.isPatched(stage)).thenReturn(false);

        SelfTestContext context = runSelfTest();

        assertEquals(SelfTestOutcome.FAILED, ramp(context).get(0).getResult());
        assertEquals("Not patched to its output port in OLA", ramp(context).get(0).getDetails());
        assertEquals(SelfTestOutcome.PASSED, ramp(context).get(1).getResult());
        assertTrue(!sent.containsKey(1), "Sent to the universe which isn't patched");
    }

    @Test
    void failsWhenOlaDoesNotTakeTheValues() throws Exception {
        universe("Stage", 1, "3-O-0");
        doReturn(false).when(lightingService).sendDmx(any(), any());

        SelfTestCheck check = ramp(runSelfTest()).getFirst();

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("OLA did not take the values", check.getDetails());
    }

    @Test
    void failsWhenOlaDoesNotRun() throws Exception {
        universe("Stage", 1, "3-O-0");
        when(olaService.getHealthyError()).thenReturn("OLA service is not active: inactive");

        SelfTestCheck check = ramp(runSelfTest()).getFirst();

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("OLA service is not active: inactive", check.getDetails());
        verify(lightingService, never()).sendDmx(any(), any());
        verify(lightingService, never()).send();
    }

    @Test
    void failsWhenTheUnitIsNotConnectedToOla() throws Exception {
        universe("Stage", 1, "3-O-0");
        capabilities.setOla(false);

        SelfTestCheck check = ramp(runSelfTest()).getFirst();

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("Not connected to OLA", check.getDetails());
    }

    @Test
    void isSkippedWithoutUniverseWithAnOutputPort() throws Exception {
        universe("Without port", 1, null);
        universe("Without OLA universe", null, "3-O-0");

        SelfTestContext context = runSelfTest();

        assertEquals(1, ramp(context).size());
        assertEquals(SelfTestOutcome.SKIPPED, ramp(context).getFirst().getResult());
        assertEquals("No DMX universe with an output port in the settings", ramp(context).getFirst().getDetails());
        verify(olaService, never()).getHealthyError();
    }

    @Test
    void stopsTheRampWithoutFlashWhenACompositionStarts() throws Exception {
        universe("Stage", 1, "3-O-0");
        dmxSelfTest.rampMillis = 10_000;
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> sent.containsKey(1) && sent.get(1).size() >= 3);

        dmxSelfTest.run(context);

        assertEquals(SelfTestOutcome.SKIPPED, ramp(context).getFirst().getResult());
        List<short[]> universe = sent.get(1);
        assertEquals(0, universe.getLast()[0]);
        assertTrue(universe.stream().allMatch(values -> values[0] < 255), "Flashed");
    }

    private static OlaPort olaPort(String id, String device) {
        OlaPort olaPort = new OlaPort();
        olaPort.setId(id);
        olaPort.setDevice(device);
        olaPort.setOutput(true);
        return olaPort;
    }

    private SelfTestCheck dmxInterface() throws Exception {
        return runSelfTest().getChecks().stream().filter(check -> check.getName().equals(DmxSelfTest.DMX_INTERFACE)).findFirst().orElseThrow();
    }

    @Test
    void findsTheOutputPortsOfOla() throws Exception {
        when(lightingService.getOlaOutputPorts()).thenReturn(List.of(olaPort("1-O-0", "Dummy Device"), olaPort("3-O-0", "Enttec Usb Pro Device")));

        SelfTestCheck check = dmxInterface();

        assertEquals(SelfTestOutcome.PASSED, check.getResult());
        assertEquals("Enttec Usb Pro Device (3-O-0)", check.getDetails());
    }

    @Test
    void theInterfaceIsSkippedWithOnlyTheDummyPortOrWithoutOla() throws Exception {
        when(lightingService.getOlaOutputPorts()).thenReturn(List.of(olaPort("1-O-0", "Dummy Device")));
        assertEquals("OLA offers no output port: no DMX interface, or its plugin is off in the lighting settings", dmxInterface().getDetails());

        capabilities.setOla(false);
        assertEquals(SelfTestOutcome.SKIPPED, dmxInterface().getResult());
        assertEquals("OLA is not available", dmxInterface().getDetails());
    }

}
