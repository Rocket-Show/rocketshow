package com.ascargon.rocketshow.lighting.designer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The audio a pixel effect follows at a moment: frames of an analysis of the audio of the composition, each 20 ms
 * long, which say how loud it is (the RMS and the peak), how loud the bass, the mids and the highs are and each of 16
 * bands from the lowest to the highest, all in dB (0 at full scale). Rocket Show analyzes the audio while it plays
 * (AudioAnalysisService), the designer its audio file: what is the same is how the frames light the pixels, which both
 * do the very same way (pixel-audio.ts).
 *
 * @author Moritz A. Vieli
 */
final class PixelAudio {

    static final int FRAME_MILLIS = 20;
    static final int BANDS = 16;

    // where each is in a frame
    static final int RMS = 0;
    static final int PEAK = 1;
    static final int BASS = 2;
    static final int MID = 3;
    static final int HIGH = 4;
    static final int FIRST_BAND = 5;
    static final int FRAME_LENGTH = FIRST_BAND + BANDS;

    // as quiet as a frame which is missing
    static final double SILENCE_DB = -100;

    // how many frames before a moment the envelope starts from silence: a second
    private static final int ENVELOPE_FRAMES = 50;

    private final IntFunction<double[]> frames;
    private final Settings settings;

    // the frame of the moment
    private final int frameIndex;
    private final Map<Long, Double> envelopes = new HashMap<>();

    /**
     * What an effect sets for the audio it follows (see EffectPixel): the band ('full', the RMS, 'bass', 'mid' or
     * 'high'), how much louder it is taken and how quiet is silent (in dB) and how fast the level follows the audio up
     * and down again (in ms).
     */
    interface Settings {
        String getAudioBand();

        double getAudioGainDb();

        double getAudioFloorDb();

        double getAudioAttackMillis();

        double getAudioReleaseMillis();
    }

    PixelAudio(IntFunction<double[]> frames, double timeMillis, Settings settings) {
        this.frames = frames;
        this.settings = settings;
        this.frameIndex = (int) Math.floor(timeMillis / FRAME_MILLIS);
    }

    // how loud an audio is between 0 (as quiet as the floor or quieter) and 1 (at full scale), with the gain
    static double levelOf(double db, Settings settings) {
        double floor = Math.min(settings.getAudioFloorDb(), -1);

        return PixelMath.clamp01((db + settings.getAudioGainDb() - floor) / -floor);
    }

    // how loud the band the effect follows is, between 0 and 1
    double level() {
        return envelope(followedIndex(), frameIndex);
    }

    // how loud the band the effect follows was the passed number of frames before
    double levelAgo(int frames) {
        return envelope(followedIndex(), frameIndex - frames);
    }

    // how loud a band of the 16 is (0 the lowest)
    double band(int band) {
        return envelope(FIRST_BAND + Math.max(0, Math.min(BANDS - 1, band)), frameIndex);
    }

    private int followedIndex() {
        return switch (settings.getAudioBand() == null ? "" : settings.getAudioBand()) {
            case "bass" -> BASS;
            case "mid" -> MID;
            case "high" -> HIGH;
            default -> RMS;
        };
    }

    // the envelope of a value of the frames at a frame, worked out once: rising as fast as the attack and falling as fast
    // as the release lets it, from silence a second before
    private double envelope(int valueIndex, int frameIndex) {
        long key = (long) frameIndex * 32 + valueIndex;
        Double known = envelopes.get(key);

        if (known != null) {
            return known;
        }

        double attack = FRAME_MILLIS / (Math.max(settings.getAudioAttackMillis(), 0) + FRAME_MILLIS);
        double release = FRAME_MILLIS / (Math.max(settings.getAudioReleaseMillis(), 0) + FRAME_MILLIS);
        double envelope = 0;

        for (int i = frameIndex - ENVELOPE_FRAMES; i <= frameIndex; i++) {
            double[] frame = i >= 0 ? frames.apply(i) : null;
            double db = frame != null && valueIndex < frame.length ? frame[valueIndex] : SILENCE_DB;
            double level = levelOf(db, settings);

            envelope = envelope + (level - envelope) * (level > envelope ? attack : release);
        }

        envelopes.put(key, envelope);

        return envelope;
    }

}
