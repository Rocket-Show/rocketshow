package com.ascargon.rocketshow.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the analysis keeps of the levels and the spectra GStreamer measures while a composition plays, which the pixel
 * effects follow.
 */
class AudioAnalysisServiceTest {

    @Test
    void keepsTheLevelOfEach20MillisecondsAsLoudAsItsChannelsOnAverage() {
        AudioAnalysisService service = new AudioAnalysisService();

        // two channels, one 3 dB louder than the other, 30 ms into the stream
        service.addLevel(30_000_000L, new double[]{-10, -13}, new double[]{-2, -6});

        double[] frame = service.frame(1);
        assertEquals(-11.25, frame[0], 0.01);
        assertEquals(-2, frame[1]);
        // the frequencies are silent until their spectrum is measured
        assertEquals(AudioAnalysisService.SILENCE_DB, frame[2]);
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

        service.addSpectrum(0, magnitudes, 48000);

        double[] frame = service.frame(0);
        // in the mids, not in the bass nor the highs
        assertEquals(0, frame[3], 0.01);
        assertTrue(frame[2] < -60);
        assertTrue(frame[4] < -60);
        // the band of the 16 which goes from 800 to 1164 Hz
        assertEquals(0, frame[5 + 8], 0.01);
        assertTrue(frame[5 + 7] < -60);
    }

    @Test
    void forgetsWhatItMeasuredWhenANewCompositionPlays() {
        AudioAnalysisService service = new AudioAnalysisService();
        service.addLevel(0, new double[]{-20}, new double[]{-10});
        service.setRequired(true);

        service.clear();

        assertNull(service.frame(0));
        assertTrue(service.isRequired());
        service.setRequired(false);
        assertFalse(service.isRequired());
    }

    @Test
    void keepsTheFramesOfTheLastMinutesOnly() {
        AudioAnalysisService service = new AudioAnalysisService();
        service.addLevel(0, new double[]{-20}, new double[]{-10});

        // six minutes later
        service.addLevel(6L * 60 * 1000 * 1_000_000, new double[]{-20}, new double[]{-10});

        assertNull(service.frame(0));
    }

    @Test
    void keepsARecordedFrameWithSilenceWhereItIsShort() {
        AudioAnalysisService service = new AudioAnalysisService();

        service.setFrame(3, new double[]{-12, -6});

        assertEquals(AudioAnalysisService.FRAME_LENGTH, service.frame(3).length);
        assertEquals(-6, service.frame(3)[1]);
        assertEquals(AudioAnalysisService.SILENCE_DB, service.frame(3)[20]);
    }

}
