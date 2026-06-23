package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

class MidiTimecodeMessageFactory {

    private static final int QUARTER_FRAME_STATUS = 0xF1;
    private static final int FULL_FRAME_UNIVERSAL_REALTIME_SYSEX = 0x7F;
    private static final int FULL_FRAME_DEVICE_ALL_CALL = 0x7F;
    private static final int FULL_FRAME_SUB_ID_TIME_CODE = 0x01;
    private static final int FULL_FRAME_SUB_ID_FULL_MESSAGE = 0x01;

    private MidiTimecodeMessageFactory() {
    }

    static ShortMessage createQuarterFrameMessage(long positionMillis, MidiTimecodeFrameRate frameRate, int messageType) throws InvalidMidiDataException {
        TimecodePosition position = getTimecodePosition(positionMillis, frameRate, 2);

        int value = switch (messageType) {
            case 0 -> position.frame() & 0x0F;
            case 1 -> (position.frame() >> 4) & 0x01;
            case 2 -> position.second() & 0x0F;
            case 3 -> (position.second() >> 4) & 0x03;
            case 4 -> position.minute() & 0x0F;
            case 5 -> (position.minute() >> 4) & 0x03;
            case 6 -> position.hour() & 0x0F;
            case 7 -> ((frameRate.getMidiRateCode() & 0x03) << 1) | ((position.hour() >> 4) & 0x01);
            default -> throw new IllegalArgumentException("Invalid MIDI timecode quarter-frame message type: " + messageType);
        };

        ShortMessage message = new ShortMessage();
        message.setMessage(QUARTER_FRAME_STATUS, ((messageType & 0x07) << 4) | (value & 0x0F), 0);
        return message;
    }

    static SysexMessage createFullFrameMessage(long positionMillis, MidiTimecodeFrameRate frameRate) throws InvalidMidiDataException {
        TimecodePosition position = getTimecodePosition(positionMillis, frameRate, 0);
        byte[] data = new byte[]{
                (byte) SysexMessage.SYSTEM_EXCLUSIVE,
                (byte) FULL_FRAME_UNIVERSAL_REALTIME_SYSEX,
                (byte) FULL_FRAME_DEVICE_ALL_CALL,
                (byte) FULL_FRAME_SUB_ID_TIME_CODE,
                (byte) FULL_FRAME_SUB_ID_FULL_MESSAGE,
                (byte) (((frameRate.getMidiRateCode() & 0x03) << 5) | (position.hour() & 0x1F)),
                (byte) (position.minute() & 0x3F),
                (byte) (position.second() & 0x3F),
                (byte) (position.frame() & 0x1F),
                (byte) SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE
        };

        SysexMessage message = new SysexMessage();
        message.setMessage(data, data.length);
        return message;
    }

    private static TimecodePosition getTimecodePosition(long positionMillis, MidiTimecodeFrameRate frameRate, int frameOffset) {
        long frameNumber = Math.max(0, Math.round(positionMillis * frameRate.getFramesPerSecond() / 1000.0)) + frameOffset;

        if (frameRate.isDropFrame()) {
            frameNumber = convertToDropFrameNumber(frameNumber);
        }

        int nominalFramesPerSecond = frameRate.getNominalFramesPerSecond();
        int frame = (int) (frameNumber % nominalFramesPerSecond);
        long totalSeconds = frameNumber / nominalFramesPerSecond;
        int second = (int) (totalSeconds % 60);
        int minute = (int) ((totalSeconds / 60) % 60);
        int hour = (int) ((totalSeconds / 3600) % 24);

        return new TimecodePosition(hour, minute, second, frame);
    }

    private static long convertToDropFrameNumber(long frameNumber) {
        long framesPer10Minutes = 17982;
        long framesPerMinute = 1798;
        long framesPer24Hours = 2589408;

        frameNumber %= framesPer24Hours;

        long tenMinuteChunks = frameNumber / framesPer10Minutes;
        long remainingFrames = frameNumber % framesPer10Minutes;
        long droppedFrames = 18 * tenMinuteChunks;

        if (remainingFrames >= 2) {
            droppedFrames += 2 * ((remainingFrames - 2) / framesPerMinute);
        }

        return frameNumber + droppedFrames;
    }

    private record TimecodePosition(int hour, int minute, int second, int frame) {
    }
}
