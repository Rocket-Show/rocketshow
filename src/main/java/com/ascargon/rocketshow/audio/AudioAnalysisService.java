package com.ascargon.rocketshow.audio;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The analysis of the audio of the composition playing, which the pixel effects of the designer follow: for each
 * 20 ms of the composition, how loud it is (the RMS and the peak of all of its channels), how loud the bass, the mids
 * and the highs are and each of 16 bands from the lowest to the highest, all in dB of full scale. The composition
 * pipeline measures it with the level and the spectrum of GStreamer while a loaded project has an effect following the
 * audio. The designer analyzes the audio file the very same way, as closely as it can (pixel-audio-analysis.ts).
 *
 * @author Moritz A. Vieli
 */
@Service
public class AudioAnalysisService {

    public static final int FRAME_MILLIS = 20;
    public static final int BANDS = 16;
    public static final int FRAME_LENGTH = 5 + BANDS;
    public static final double SILENCE_DB = -100;

    // the bands: from 40 Hz to 16 kHz, each as many times higher as the one before, and where the bass ends and the
    // highs start
    private static final double LOWEST_HZ = 40;
    private static final double HIGHEST_HZ = 16000;
    private static final double BASS_HZ = 250;
    private static final double HIGH_HZ = 4000;

    // the frames of the last minutes, by their index (the frame from 0 to 20 ms is the first one)
    private static final int KEPT_FRAMES = 5 * 60 * 1000 / FRAME_MILLIS;

    private final ConcurrentHashMap<Integer, double[]> frames = new ConcurrentHashMap<>();
    private volatile int newestFrame = -1;

    // whether the loaded project has an effect following the audio, which the pipeline is built with
    private volatile boolean required = false;

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    // a new composition plays: what was measured belongs to the one before
    public void clear() {
        frames.clear();
        newestFrame = -1;
    }

    // the frame with the passed index, none where there is none (yet)
    public double[] frame(int index) {
        return frames.get(index);
    }

    // keep a frame, e.g. one of a recorded analysis
    public void setFrame(int index, double[] frame) {
        double[] kept = new double[FRAME_LENGTH];
        Arrays.fill(kept, SILENCE_DB);
        System.arraycopy(frame, 0, kept, 0, Math.min(frame.length, FRAME_LENGTH));
        frames.put(index, kept);
        newestFrame = Math.max(newestFrame, index);
    }

    private double[] frameAt(long streamTimeNanos) {
        int index = (int) Math.floor(streamTimeNanos / 1e6 / FRAME_MILLIS);

        // the oldest ones go
        if (index > newestFrame) {
            newestFrame = index;
            frames.keySet().removeIf(kept -> kept < index - KEPT_FRAMES);
        }

        return frames.computeIfAbsent(index, key -> {
            double[] frame = new double[FRAME_LENGTH];
            Arrays.fill(frame, SILENCE_DB);
            return frame;
        });
    }

    private static double power(double db) {
        return Math.pow(10, db / 10);
    }

    private static double db(double power) {
        return power > 0 ? Math.max(10 * Math.log10(power), SILENCE_DB) : SILENCE_DB;
    }

    /**
     * A level GStreamer measured: the RMS and the peak of each channel in dB at the passed time of the stream. The
     * frame is as loud as the power of the channels on average, its peak the one of the loudest.
     */
    public void addLevel(long streamTimeNanos, double[] rmsDb, double[] peakDb) {
        if (rmsDb == null || rmsDb.length == 0) {
            return;
        }

        double power = 0;
        double peak = SILENCE_DB;

        for (int channel = 0; channel < rmsDb.length; channel++) {
            power += power(rmsDb[channel]) / rmsDb.length;

            if (peakDb != null && channel < peakDb.length) {
                peak = Math.max(peak, peakDb[channel]);
            }
        }

        double[] frame = frameAt(streamTimeNanos);
        frame[0] = db(power);
        frame[1] = Math.max(peak, SILENCE_DB);
    }

    /**
     * A spectrum GStreamer measured: the magnitude of each of its bands in dB (from 0 Hz up to half the sample rate,
     * all as wide), at the passed time of the stream. Summed into the bass, the mids and the highs and into the 16
     * bands, each band of the spectrum where its middle is.
     */
    public void addSpectrum(long streamTimeNanos, List<? extends Number> magnitudesDb, int sampleRate) {
        if (magnitudesDb == null || magnitudesDb.isEmpty()) {
            return;
        }

        int count = magnitudesDb.size();
        double width = sampleRate / 2.0 / count;
        double[] powers = new double[3 + BANDS];
        double[] edges = new double[BANDS + 1];

        for (int band = 0; band <= BANDS; band++) {
            edges[band] = LOWEST_HZ * Math.pow(HIGHEST_HZ / LOWEST_HZ, (double) band / BANDS);
        }

        for (int i = 0; i < count; i++) {
            double middle = (i + 0.5) * width;
            double power = power(magnitudesDb.get(i).doubleValue());

            if (middle >= LOWEST_HZ && middle < BASS_HZ) {
                powers[0] += power;
            } else if (middle >= BASS_HZ && middle < HIGH_HZ) {
                powers[1] += power;
            } else if (middle >= HIGH_HZ && middle < HIGHEST_HZ) {
                powers[2] += power;
            }

            for (int band = 0; band < BANDS; band++) {
                if (middle >= edges[band] && middle < edges[band + 1]) {
                    powers[3 + band] += power;
                }
            }
        }

        double[] frame = frameAt(streamTimeNanos);

        for (int i = 0; i < powers.length; i++) {
            frame[2 + i] = db(powers[i]);
        }
    }

}
