package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * The picture a pixel effect shows a text or an image with: its pixels in frames (an animation has more than one),
 * each pixel a brightness (a text, a byte each) or red, green and blue (an image, three bytes each), row by row from the
 * top left. The designer makes it when the text or the image is picked (pixel-bitmap.ts), the lighting engine reads the
 * very same bytes.
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

}
