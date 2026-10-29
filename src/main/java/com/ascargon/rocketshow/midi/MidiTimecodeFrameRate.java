package com.ascargon.rocketshow.midi;

public enum MidiTimecodeFrameRate {
    FPS_24(0, 24.0, 24),
    FPS_25(1, 25.0, 25),
    FPS_29_97_DROP(2, 29.97, 30),
    FPS_30(3, 30.0, 30);

    private final int midiRateCode;
    private final double framesPerSecond;
    private final int nominalFramesPerSecond;

    MidiTimecodeFrameRate(int midiRateCode, double framesPerSecond, int nominalFramesPerSecond) {
        this.midiRateCode = midiRateCode;
        this.framesPerSecond = framesPerSecond;
        this.nominalFramesPerSecond = nominalFramesPerSecond;
    }

    int getMidiRateCode() {
        return midiRateCode;
    }

    double getFramesPerSecond() {
        return framesPerSecond;
    }

    int getNominalFramesPerSecond() {
        return nominalFramesPerSecond;
    }

    boolean isDropFrame() {
        return this == FPS_29_97_DROP;
    }

    /**
     * The frame rate a received timecode announces, falling back to 30fps for a rate code that no
     * frame rate uses.
     */
    static MidiTimecodeFrameRate fromMidiRateCode(int midiRateCode) {
        for (MidiTimecodeFrameRate frameRate : values()) {
            if (frameRate.getMidiRateCode() == midiRateCode) {
                return frameRate;
            }
        }

        return FPS_30;
    }
}
