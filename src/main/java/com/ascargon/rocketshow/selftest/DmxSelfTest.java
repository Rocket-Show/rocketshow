package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends a ramp on every channel of every universe of the settings with an output port, then zero. Whether the signal
 * is electrically right needs a DMX receiver.
 */
@Service
class DmxSelfTest implements SelfTestStep {

    static final String DMX = "dmx";

    private static final long FRAME_MILLIS = 40;

    private final SettingsService settingsService;
    private final CapabilitiesService capabilitiesService;
    private final LightingService lightingService;
    private final OlaService olaService;

    // Package visible for the tests
    long rampMillis = 3000;

    // Whether the self-test sent DMX, which replaced the values of the unit
    private volatile boolean sent = false;

    DmxSelfTest(SettingsService settingsService, CapabilitiesService capabilitiesService, LightingService lightingService, OlaService olaService) {
        this.settingsService = settingsService;
        this.capabilitiesService = capabilitiesService;
        this.lightingService = lightingService;
        this.olaService = olaService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(DMX);
    }

    private static String getTarget(LightingUniverse lightingUniverse) {
        String name = lightingUniverse.getName() == null || lightingUniverse.getName().isBlank() ? "Universe" : lightingUniverse.getName();
        return name + ", OLA universe " + lightingUniverse.getOlaUniverseId() + " on port " + lightingUniverse.getOlaOutputPortId();
    }

    private static short[] universe(int value) {
        short[] values = new short[512];
        Arrays.fill(values, (short) value);
        return values;
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        List<LightingUniverse> lightingUniverses = settingsService.getSettings().getLightingUniverseList().stream()
                .filter(lightingUniverse -> lightingUniverse.getOlaUniverseId() != null
                        && lightingUniverse.getOlaOutputPortId() != null && !lightingUniverse.getOlaOutputPortId().isBlank())
                .toList();

        if (lightingUniverses.isEmpty()) {
            context.report(SelfTestCheck.skipped(DMX, null, "No DMX universe with an output port in the settings"));
            return;
        }

        String olaError = olaService.getHealthyError();
        if (olaError == null && !capabilitiesService.getCapabilities().isOla()) {
            olaError = "Not connected to OLA";
        }
        if (olaError != null) {
            context.report(SelfTestCheck.failed(DMX, null, olaError));
            return;
        }

        // The error of each universe, or null
        Map<LightingUniverse, String> errors = new LinkedHashMap<>();
        for (LightingUniverse lightingUniverse : lightingUniverses) {
            errors.put(lightingUniverse, lightingService.isPatched(lightingUniverse) ? null : "Not patched to its output port in OLA");
        }

        sent = true;
        long start = System.currentTimeMillis();
        boolean stopped = false;

        while (!stopped && System.currentTimeMillis() - start < rampMillis) {
            short[] values = universe((int) Math.min(255, 255 * (System.currentTimeMillis() - start) / rampMillis));
            send(errors, values);
            stopped = !context.await(FRAME_MILLIS);
        }

        if (!stopped) {
            send(errors, universe(255));
        }
        send(errors, universe(0));

        for (Map.Entry<LightingUniverse, String> error : errors.entrySet()) {
            String target = getTarget(error.getKey());

            if (error.getValue() != null) {
                context.report(SelfTestCheck.failed(DMX, target, error.getValue()));
            } else if (stopped) {
                context.report(SelfTestCheck.skipped(DMX, target, SelfTestContext.STOPPED));
            } else {
                context.report(SelfTestCheck.passed(DMX, target, "Ramp from 0 to 255 on 512 channels sent in " + rampMillis / 1000.0 + " s"));
            }
        }
    }

    private void send(Map<LightingUniverse, String> errors, short[] values) {
        for (Map.Entry<LightingUniverse, String> error : errors.entrySet()) {
            if (error.getValue() == null && !lightingService.sendDmx(error.getKey(), values)) {
                error.setValue("OLA did not take the values");
            }
        }
    }

    @Override
    public void restore() {
        // The values of the unit again, e.g. of the designer's live preview
        if (sent) {
            sent = false;
            lightingService.send();
        }
    }

}
