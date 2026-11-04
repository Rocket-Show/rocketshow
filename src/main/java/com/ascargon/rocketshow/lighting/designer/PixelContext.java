package com.ascargon.rocketshow.lighting.designer;

/**
 * Where a pixel is: its place in the picture (x from the left to the right, y from the top to the bottom, both from 0
 * to 1, the middle of the pixel), the column and the row it is in and how many there are. A bar is a matrix with one
 * row. The aspect says how wide and high the picture is, the longer side being 1. Reused from pixel to pixel, so a
 * frame does not create one for each of them.
 *
 * @author Moritz A. Vieli
 */
class PixelContext {

    double x = 0.5;
    double y = 0.5;
    int column = 0;
    int row = 0;
    int columns = 1;
    int rows = 1;
    double aspectX = 1;
    double aspectY = 1;

    // a whole number the random looking patterns start from (of the effect and the fixture)
    int seed = 0;

}
