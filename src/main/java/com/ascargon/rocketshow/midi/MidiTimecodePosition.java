package com.ascargon.rocketshow.midi;

/**
 * An absolute SMPTE position as carried by MIDI timecode (MTC).
 * <p>
 * {@link MidiTimecodeMessageFactory} converts a playback position into this representation to send
 * it, {@link MidiTimecodeDecoder} assembles it from received messages and converts it back with
 * {@link #toMillis(MidiTimecodeFrameRate)}.
 */
record MidiTimecodePosition(int hour, int minute, int second, int frame) {

    /**
     * The absolute time this position represents, in milliseconds. The inverse of the conversion
     * done in {@link MidiTimecodeMessageFactory}.
     */
    long toMillis(MidiTimecodeFrameRate frameRate) {
        long frameNumber;

        if (frameRate.isDropFrame()) {
            // Drop-frame timecode skips the labels :00 and :01 of every minute except every tenth
            // minute, so the labelled frame number runs ahead of the real one. Subtract the skipped
            // labels again to get back to a real (29.97 Hz) frame number.
            long totalMinutes = 60L * hour + minute;
            frameNumber = 108000L * hour + 1800L * minute + 30L * second + frame
                    - 2 * (totalMinutes - totalMinutes / 10);
        } else {
            frameNumber = ((hour * 60L + minute) * 60L + second) * frameRate.getNominalFramesPerSecond() + frame;
        }

        return Math.round(frameNumber * 1000.0 / frameRate.getFramesPerSecond());
    }

}
