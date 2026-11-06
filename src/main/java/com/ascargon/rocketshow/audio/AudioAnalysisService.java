package com.ascargon.rocketshow.audio;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The analysis of the audio of the composition playing, which the pixel effects of the designer follow: for each
 * 20 ms of the composition and each audio bus, how loud it is (the RMS and the peak of the channels of the bus), how
 * loud the bass, the mids and the highs are and each of 16 bands from the lowest to the highest, all in dB of full
 * scale. The composition pipeline measures each bus a pixel effect of the loaded project follows, with the level and
 * the spectrum of GStreamer. An effect following several buses (or all of them) follows them added together, the way
 * they sound together. The designer analyzes the audio files the very same way, as closely as it can
 * (pixel-audio-analysis.ts).
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

    // the peak is the loudest one of the buses, the other values add up
    private static final int PEAK = 1;

    // the frames of the last minutes, by their index (the frame from 0 to 20 ms is the first one)
    private static final int KEPT_FRAMES = 5 * 60 * 1000 / FRAME_MILLIS;

    // the frames of each bus measured, by the uuid of the bus
    private final Map<String, ConcurrentHashMap<Integer, double[]>> frames = new ConcurrentHashMap<>();
    private volatile int newestFrame = -1;

    // the buses a pixel effect of the loaded project follows, which the pipeline measures
    private volatile Set<String> requiredBuses = Set.of();

    public boolean isRequired() {
        return !requiredBuses.isEmpty();
    }

    public boolean isRequired(String busUuid) {
        return requiredBuses.contains(busUuid);
    }

    public Set<String> getRequiredBuses() {
        return requiredBuses;
    }

    public void setRequiredBuses(Collection<String> busUuids) {
        this.requiredBuses = busUuids == null ? Set.of() : Set.copyOf(new HashSet<>(busUuids));
    }

    // a new composition plays: what was measured belongs to the one before
    public void clear() {
        frames.clear();
        newestFrame = -1;
    }

    // the frame of all buses measured with the passed index, none where there is none (yet)
    public double[] frame(int index) {
        return frame(List.of(), index);
    }

    // The frame of the passed buses with the passed index (of all buses measured, if none are passed): the buses
    // added together. None where none of them has one (yet).
    public double[] frame(Collection<String> busUuids, int index) {
        List<double[]> busFrames = new ArrayList<>();

        if (busUuids == null || busUuids.isEmpty()) {
            for (Map<Integer, double[]> busFramesByIndex : frames.values()) {
                addFrame(busFrames, busFramesByIndex.get(index));
            }
        } else {
            for (String busUuid : new HashSet<>(busUuids)) {
                Map<Integer, double[]> busFramesByIndex = frames.get(busUuid);
                addFrame(busFrames, busFramesByIndex == null ? null : busFramesByIndex.get(index));
            }
        }

        if (busFrames.isEmpty()) {
            return null;
        }

        return busFrames.size() == 1 ? busFrames.getFirst() : added(busFrames);
    }

    private static void addFrame(List<double[]> busFrames, double[] frame) {
        if (frame != null) {
            busFrames.add(frame);
        }
    }

    // buses sounding together: their power added up, the loudest peak
    private static double[] added(List<double[]> busFrames) {
        double[] frame = new double[FRAME_LENGTH];

        for (int i = 0; i < FRAME_LENGTH; i++) {
            double power = 0;
            double peak = SILENCE_DB;

            for (double[] busFrame : busFrames) {
                power += power(busFrame[i]);
                peak = Math.max(peak, busFrame[i]);
            }

            frame[i] = i == PEAK ? peak : db(power);
        }

        return frame;
    }

    // keep a frame of a bus, e.g. one of a recorded analysis
    public void setFrame(String busUuid, int index, double[] frame) {
        double[] kept = new double[FRAME_LENGTH];
        Arrays.fill(kept, SILENCE_DB);
        System.arraycopy(frame, 0, kept, 0, Math.min(frame.length, FRAME_LENGTH));
        framesOf(busUuid).put(index, kept);
        newestFrame = Math.max(newestFrame, index);
    }

    private ConcurrentHashMap<Integer, double[]> framesOf(String busUuid) {
        return frames.computeIfAbsent(busUuid == null ? "" : busUuid, key -> new ConcurrentHashMap<>());
    }

    private double[] frameAt(String busUuid, long streamTimeNanos) {
        int index = (int) Math.floor(streamTimeNanos / 1e6 / FRAME_MILLIS);

        // the oldest ones go
        if (index > newestFrame) {
            newestFrame = index;

            for (Map<Integer, double[]> busFrames : frames.values()) {
                busFrames.keySet().removeIf(kept -> kept < index - KEPT_FRAMES);
            }
        }

        return framesOf(busUuid).computeIfAbsent(index, key -> {
            double[] frame = new double[FRAME_LENGTH];
            Arrays.fill(frame, SILENCE_DB);
            return frame;
        });
    }

    private static double power(double db) {
        return db <= SILENCE_DB ? 0 : Math.pow(10, db / 10);
    }

    private static double db(double power) {
        return power > 0 ? Math.max(10 * Math.log10(power), SILENCE_DB) : SILENCE_DB;
    }

    /**
     * A level GStreamer measured on a bus: the RMS and the peak of each of its channels in dB at the passed time of the
     * stream. The frame is as loud as the power of the channels on average, its peak the one of the loudest.
     */
    public void addLevel(String busUuid, long streamTimeNanos, double[] rmsDb, double[] peakDb) {
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

        double[] frame = frameAt(busUuid, streamTimeNanos);
        frame[0] = db(power);
        frame[PEAK] = Math.max(peak, SILENCE_DB);
    }

    /**
     * A spectrum GStreamer measured on a bus: the magnitude of each of its bands in dB (from 0 Hz up to half the sample
     * rate, all as wide), at the passed time of the stream. Summed into the bass, the mids and the highs and into the
     * 16 bands, each band of the spectrum where its middle is.
     */
    public void addSpectrum(String busUuid, long streamTimeNanos, List<? extends Number> magnitudesDb, int sampleRate) {
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

        double[] frame = frameAt(busUuid, streamTimeNanos);

        for (int i = 0; i < powers.length; i++) {
            frame[2 + i] = db(powers[i]);
        }
    }

}
