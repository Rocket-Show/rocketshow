package com.ascargon.rocketshow.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the analysis keeps of the levels and the spectra GStreamer measures on the audio buses while a composition
 * plays, which the pixel effects follow, alone or added together.
 */
class AudioAnalysisServiceTest {

    @Test
    void keepsTheLevelOfEach20MillisecondsAsLoudAsTheChannelsOfTheBusOnAverage() {
        AudioAnalysisService service = new AudioAnalysisService();

        // two channels, one 3 dB louder than the other, 30 ms into the stream
        service.addLevel("music", 30_000_000L, new double[]{-10, -13}, new double[]{-2, -6});

        double[] frame = service.frame(List.of("music"), 1);
        assertEquals(-11.25, frame[0], 0.01);
        assertEquals(-2, frame[1]);
        // the frequencies are silent until their spectrum is measured
        assertEquals(AudioAnalysisService.SILENCE_DB, frame[2]);
        assertNull(service.frame(List.of("music"), 0));
        assertNull(service.frame(List.of("click"), 1));

        // nothing measured, nothing kept
        service.addLevel("music", 0, new double[0], null);
        service.addSpectrum("music", 0, List.of(), 48000);
        assertNull(service.frame(0));
    }

    @Test
    void sumsTheBandsOfTheSpectrumIntoTheBassTheMidsTheHighsAndTheSixteenBands() {
        AudioAnalysisService service = new AudioAnalysisService();
        List<Float> magnitudes = new ArrayList<>();

        // 128 bands up to 24 kHz, of 187.5 Hz each: one tone at 0 dB in the band from 937.5 to 1125 Hz
        for (int band = 0; band < 128; band++) {
            magnitudes.add(band == 5 ? 0f : -100f);
        }

        service.addSpectrum("music", 0, magnitudes, 48000);

        double[] frame = service.frame(0);
        // in the mids, not in the bass nor the highs, which are silent
        assertEquals(0, frame[3], 0.01);
        assertEquals(AudioAnalysisService.SILENCE_DB, frame[2]);
        assertEquals(AudioAnalysisService.SILENCE_DB, frame[4]);
        // the band of the 16 which goes from 800 to 1164 Hz
        assertEquals(0, frame[5 + 8], 0.01);
        assertEquals(AudioAnalysisService.SILENCE_DB, frame[5 + 7]);
    }

    @Test
    void addsTheBusesAnEffectFollowsTogetherTheWayTheySoundTogether() {
        AudioAnalysisService service = new AudioAnalysisService();

        service.addLevel("music", 0, new double[]{-10}, new double[]{-4});
        service.addLevel("drums", 0, new double[]{-10}, new double[]{-1});
        service.addLevel("click", 0, new double[]{-100}, new double[]{-100});

        // as loud as twice the power of one, the louder peak
        double[] both = service.frame(List.of("music", "drums"), 0);
        assertEquals(-6.99, both[0], 0.01);
        assertEquals(-1, both[1]);

        // a silent bus adds nothing, a missing one neither
        double[] all = service.frame(0);
        assertEquals(-6.99, all[0], 0.01);
        assertEquals(both[0], service.frame(List.of("music", "drums", "click", "gone"), 0)[0]);
        assertEquals(AudioAnalysisService.SILENCE_DB, all[2]);

        // a single bus as it was measured
        assertSame(service.frame(List.of("music"), 0), service.frame(List.of("music", "gone"), 0));
        assertNull(service.frame(List.of("gone"), 0));
        assertEquals(-6.99, service.frame(null, 0)[0], 0.01);
    }

    @Test
    void measuresTheBusesTheEffectsFollowAndForgetsWhatItMeasuredWhenANewCompositionPlays() {
        AudioAnalysisService service = new AudioAnalysisService();
        service.addLevel("music", 0, new double[]{-20}, new double[]{-10});

        assertFalse(service.isRequired());

        service.setRequiredBuses(List.of("music", "drums", "music"));
        service.clear();

        assertNull(service.frame(0));
        assertTrue(service.isRequired());
        assertTrue(service.isRequired("drums"));
        assertFalse(service.isRequired("click"));
        assertEquals(Set.of("music", "drums"), service.getRequiredBuses());

        service.setRequiredBuses(null);
        assertFalse(service.isRequired());
    }

    @Test
    void keepsTheFramesOfTheLastMinutesOnly() {
        AudioAnalysisService service = new AudioAnalysisService();
        service.addLevel("music", 0, new double[]{-20}, new double[]{-10});
        service.addLevel("drums", 0, new double[]{-20}, new double[]{-10});

        // six minutes later
        service.addLevel("music", 6L * 60 * 1000 * 1_000_000, new double[]{-20}, new double[]{-10});

        assertNull(service.frame(0));
    }

    @Test
    void keepsARecordedFrameWithSilenceWhereItIsShort() {
        AudioAnalysisService service = new AudioAnalysisService();

        service.setFrame("", 3, new double[]{-12, -6});

        assertEquals(AudioAnalysisService.FRAME_LENGTH, service.frame(3).length);
        assertEquals(-6, service.frame(3)[1]);
        assertEquals(AudioAnalysisService.SILENCE_DB, service.frame(3)[20]);

        // of a bus without a name, which counts for all
        service.setFrame(null, 4, new double[]{-12});
        assertEquals(-12, service.frame(4)[0]);
    }

}
