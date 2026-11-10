package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The picture of a text or an image of a pixel effect (see PixelBitmap): its frames, the bytes of their pixels up to
 * its edges, and the frame shown at a moment.
 */
class PixelBitmapTest {

    private static PixelBitmap bitmap(String json) {
        return DesignerJson.MAPPER.readValue(json, PixelBitmap.class);
    }

    @Test
    void decodesEachFrameOnceAndNoneWhichCanNotBeRead() {
        // the bytes 10, 20, 30, 40 and 50, a frame which is no Base64 and one which is missing
        PixelBitmap bitmap = bitmap("{\"width\": 2, \"height\": 2, \"frames\": [\"ChQeKDI=\", \"!!!\"]}");

        assertArrayEquals(new byte[]{10, 20, 30, 40, 50}, bitmap.frame(0));
        assertEquals(0, bitmap.frame(1).length);
        assertEquals(0, bitmap.frame(2).length);
        assertEquals(0, bitmap.frame(7).length);

        // decoded once
        assertEquals(bitmap.frame(0), bitmap.frame(0));

        // a frame without its picture
        PixelBitmap missing = bitmap("{\"width\": 2, \"height\": 2}");
        missing.setFrames(java.util.Arrays.asList("ChQeKDI=", null));
        assertEquals(0, missing.frame(1).length);
    }

    @Test
    void readsThePixelsUpToItsEdges() {
        PixelBitmap bitmap = bitmap("{\"width\": 2, \"height\": 2, \"frames\": [\"ChQeKDI=\"]}");
        byte[] frame = bitmap.frame(0);

        assertEquals(10, bitmap.value(frame, 0, 0, 0));
        assertEquals(20, bitmap.value(frame, 1, 0, 0));
        assertEquals(30, bitmap.value(frame, 0, 1, 0));
        assertEquals(40, bitmap.value(frame, 1, 1, 0));

        // outside of the picture, though the bytes go on
        assertEquals(0, bitmap.value(frame, 2, 0, 0));
        assertEquals(0, bitmap.value(frame, 0, 2, 0));
        assertEquals(0, bitmap.value(frame, -1, 0, 0));
        assertEquals(0, bitmap.value(frame, 0, -1, 0));
    }

    @Test
    void readsTheColorsOfAPixelAndNothingBeyondTheBytesThereAre() {
        // a red pixel, and a green one cut off after its red
        PixelBitmap bitmap = bitmap("{\"width\": 2, \"height\": 1, \"channels\": 3, \"frames\": [\"/wAAAA==\"]}");
        byte[] frame = bitmap.frame(0);

        assertEquals(255, bitmap.value(frame, 0, 0, 0));
        assertEquals(0, bitmap.value(frame, 0, 0, 1));
        assertEquals(0, bitmap.value(frame, 1, 0, 0));
        assertEquals(0, bitmap.value(frame, 1, 0, 1));
        assertEquals(0, bitmap.value(frame, 1, 0, 2));
    }

    @Test
    void showsEachFrameAsLongAsItLasts() {
        // 100 ms, 300 ms and the default of 100 ms for the one without a length
        PixelBitmap bitmap = bitmap("{\"width\": 1, \"height\": 1, \"frames\": [\"AA==\", \"AA==\", \"AA==\"], \"durations\": [100, 300]}");

        assertEquals(0, bitmap.frameAt(0));
        assertEquals(0, bitmap.frameAt(0.19));
        assertEquals(1, bitmap.frameAt(0.21));
        assertEquals(1, bitmap.frameAt(0.79));
        assertEquals(2, bitmap.frameAt(0.81));
        // the end of the period shows the last frame
        assertEquals(2, bitmap.frameAt(1));
    }

    @Test
    void showsTheFirstFrameOfAPictureWhichDoesNotMove() {
        assertEquals(0, bitmap("{\"frames\": [\"AA==\"]}").frameAt(0.7));
        assertEquals(0, bitmap("{\"frames\": []}").frameAt(0.7));
    }

}
