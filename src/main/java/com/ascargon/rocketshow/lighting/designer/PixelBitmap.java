package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The picture a pixel effect shows a text or an image with: its pixels in frames (an animation has more than one),
 * each pixel a brightness (a text, a byte each) or red, green and blue (an image, three bytes each), row by row from the
 * top left. The designer makes it when the text or the image is picked (pixel-bitmap.ts), this reads the very same
 * bytes.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class PixelBitmap {

    private int width = 0;
    private int height = 0;

    // the bytes of each pixel: 1 (a brightness) or 3 (red, green and blue)
    private int channels = 1;

    // the frames, each the bytes of its pixels in base 64
    private List<String> frames = new ArrayList<>();

    // how long each frame of an animation shows, in ms: the frames share the period of the effect by them
    private List<Double> durations = new ArrayList<>();

    // where each letter of a text starts, in columns of the picture, for the colors of the letters
    private List<Integer> letterStarts = new ArrayList<>();

    // the frames, once decoded
    @JsonIgnore
    private final transient List<byte[]> decoded = new ArrayList<>();

    // a value missing or null keeps the default, like in the designer
    public void setWidth(Integer width) {
        this.width = width == null ? 0 : width;
    }

    public void setHeight(Integer height) {
        this.height = height == null ? 0 : height;
    }

    public void setChannels(Integer channels) {
        this.channels = channels != null && channels == 3 ? 3 : 1;
    }

    public void setFrames(List<String> frames) {
        this.frames = frames == null ? new ArrayList<>() : frames;
    }

    public void setDurations(List<Double> durations) {
        this.durations = durations == null ? new ArrayList<>() : durations;
    }

    public void setLetterStarts(List<Integer> letterStarts) {
        this.letterStarts = letterStarts == null ? new ArrayList<>() : letterStarts;
    }

    // the bytes of a frame, decoded once (none for a frame which can't be read)
    synchronized byte[] frame(int index) {
        while (decoded.size() < frames.size()) {
            decoded.add(null);
        }

        if (index >= frames.size()) {
            return new byte[0];
        }

        if (decoded.get(index) == null) {
            decoded.set(index, decode(frames.get(index)));
        }

        return decoded.get(index);
    }

    private static byte[] decode(String text) {
        if (text == null) {
            return new byte[0];
        }

        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    // A byte of a pixel of a frame: of its brightness (channel 0) or of red, green and blue (0 to 2). 0 outside of the
    // picture and beyond the bytes there are.
    int value(byte[] frame, int x, int y, int channel) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }

        long index = ((long) y * width + x) * channels + channel;

        return index < frame.length ? frame[(int) index] & 0xff : 0;
    }

    // The frame shown at a moment of the period of the effect (0 to 1): the frames one after the other, each as long as
    // it lasts. A frame of no length (or one without a length) lasts 100 ms.
    int frameAt(double moment) {
        int count = frames.size();

        if (count <= 1) {
            return 0;
        }

        double total = 0;

        for (int i = 0; i < count; i++) {
            total += frameDuration(i);
        }

        double at = moment * total;
        double end = 0;

        for (int i = 0; i < count; i++) {
            end += frameDuration(i);

            if (at < end) {
                return i;
            }
        }

        return count - 1;
    }

    private double frameDuration(int index) {
        Double duration = index < durations.size() ? durations.get(index) : null;
        return duration != null && duration > 0 ? duration : 100;
    }

}
