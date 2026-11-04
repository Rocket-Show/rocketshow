package com.ascargon.rocketshow.lighting.designer;

/**
 * The patterns of the pixel effects: where a pattern puts a pixel in the palette and how bright it makes it, at a
 * moment. The designer works them out the very same way (pixel-pattern.ts), with the same operations in the same order.
 *
 * @author Moritz A. Vieli
 */
final class PixelPatterns {

    private static final double FULL_CYCLE = 2 * Math.PI;

    // The outline of a heart, as how far it is from its middle in each of 180 directions (the longest one being 1),
    // worked out once from (x² + y² - 1)³ = x² y³ by halving the distance until it is found
    private static final double[] HEART_OUTLINE = heartOutline();

    private PixelPatterns() {
    }

    private static double[] heartOutline() {
        int directions = 180;
        double[] outline = new double[directions];
        double longest = 0;

        for (int i = 0; i < directions; i++) {
            double angle = (double) i / directions;
            double dx = CycleSine.sin(angle + 0.25);
            double dy = CycleSine.sin(angle);
            double low = 0;
            double high = 2;

            for (int step = 0; step < 24; step++) {
                double middle = (low + high) / 2;

                if (insideHeart(dx * middle, 0.12 + dy * middle)) {
                    low = middle;
                } else {
                    high = middle;
                }
            }

            outline[i] = low;
            longest = Math.max(longest, low);
        }

        for (int i = 0; i < directions; i++) {
            outline[i] = outline[i] / longest;
        }

        return outline;
    }

    private static boolean insideHeart(double x, double y) {
        double a = x * x + y * y - 1;
        return a * a * a - x * x * y * y * y <= 0;
    }

    // how far along the direction a pixel is, from 0 to 1
    private static double along(PixelContext context, String direction) {
        return switch (direction == null ? "" : direction) {
            case "left" -> 1 - context.x;
            case "up" -> 1 - context.y;
            case "down" -> context.y;
            case "out" -> Math.abs(context.x - 0.5) * 2;
            case "in" -> 1 - Math.abs(context.x - 0.5) * 2;
            default -> context.x;
        };
    }

    // how many pixels there are along the direction, to soften an edge over at least half of one
    private static int pixelsAlong(PixelContext context, String direction) {
        return "up".equals(direction) || "down".equals(direction) ? context.rows : context.columns;
    }

    // Is the picture a matrix, rather than a bar? A matrix has rows, which are not much shorter than its columns.
    private static boolean isMatrix(PixelContext context) {
        return context.rows > 1 && context.aspectY > 0.2;
    }

    // how far a point (from the middle, -1 to 1 on each axis, up being positive) is out in a shape: 1 on its outline
    private static double shapeDistance(double x, double y, String shape) {
        return switch (shape == null ? "" : shape) {
            case "square" -> Math.max(Math.abs(x), Math.abs(y));
            case "diamond" -> (Math.abs(x) + Math.abs(y)) * 0.72;
            case "star" -> {
                double angle = ArcTangent.atan2(y, x) / FULL_CYCLE;
                // five points, one of them up
                double outline = 0.62 + 0.38 * CycleSine.sin(5 * (angle - 0.25) + 0.25);

                yield (Math.sqrt(x * x + y * y) / outline) * 0.78;
            }
            case "heart" -> {
                double angle = PixelMath.fract(ArcTangent.atan2(y, x) / FULL_CYCLE);
                double outline = HEART_OUTLINE[(int) Math.floor(angle * HEART_OUTLINE.length) % HEART_OUTLINE.length];

                yield Math.sqrt(x * x + y * y) / outline;
            }
            default -> Math.sqrt(x * x + y * y);
        };
    }

    // A pattern at a pixel and a moment: where it puts the pixel in the palette (any number, the palette repeats) into
    // result[0] and how bright the pixel is (between 0 and 1) into result[1]. The moment is counted in periods of the
    // effect since it started.
    static void sample(PixelPatternSettings settings, double cycle, PixelContext context, double[] result) {
        double size = settings.getSize();
        double softness = settings.getSoftness();
        String direction = settings.getDirection();
        String pattern = settings.getPattern() == null ? "" : settings.getPattern();

        switch (pattern) {
            case "chase" -> {
                // a block running along, every pass in the next color
                double position = along(context, direction);
                double width = Math.max(size, 0.02);
                double middle = PixelMath.fract(cycle) * (1 + width) - width / 2;
                double edge = Math.max(softness * width * 0.5, 0.5 / pixelsAlong(context, direction));

                set(result, Math.floor(cycle) * 0.2, PixelMath.band(Math.abs(position - middle), width / 2, edge));
            }
            case "comet" -> {
                // a head with a tail fading behind it through the palette
                double position = along(context, direction);
                double tail = Math.max(size, 0.05);
                double behind;

                if (settings.isBounce()) {
                    double head = PixelMath.triangle(cycle);
                    behind = PixelMath.fract(cycle) < 0.5 ? head - position : position - head;
                } else {
                    behind = PixelMath.fract(cycle) * (1 + tail) - position;
                }

                if (behind < 0) {
                    // a glow in front of the head, over about a pixel
                    double pixels = behind * pixelsAlong(context, direction);
                    double glow = Math.max(0, 1 - pixels * pixels * 0.8);

                    set(result, 1, glow * glow);
                } else if (behind > tail) {
                    set(result, 0, 0);
                } else {
                    double left = 1 - behind / tail;

                    set(result, left, left * left);
                }
            }
            case "wipe" -> {
                // the next color of the palette paints over the last one
                double position = along(context, direction);
                double edge = Math.max(softness * 0.2, 0.5 / pixelsAlong(context, direction));
                double progress = PixelMath.fract(cycle);
                double painted = 1 - PixelMath.smoothStep(progress - edge, progress + edge, position * (1 - 2 * edge) + edge);

                set(result, (Math.floor(cycle) + painted) * (0.15 + size * 0.6), 1);
            }
            // the palette moving along
            case "flow" -> set(result, along(context, direction) * (0.35 + (1 - size) * 2.2) - cycle, 1);
            case "sparkle" -> {
                // pixels flashing up at random and fading out
                int column = context.column + context.seed;
                double offset = cycle * 2 + PixelMath.hash(column, context.row, 11);
                double slot = Math.floor(offset);
                double sinceFlash = offset - slot;
                int slotNumber = PixelMath.toInt(slot);

                if (PixelMath.hash(column, context.row, slotNumber * 7 + 3) >= size * 0.65 + 0.04) {
                    set(result, 0, 0);
                } else {
                    double fade = 0.15 + softness * 0.85;
                    double left = PixelMath.clamp01(1 - (sinceFlash - 0.04) / fade);

                    set(result, PixelMath.hash(column, context.row, slotNumber * 13 + 5), sinceFlash < 0.04 ? sinceFlash / 0.04 : left * left);
                }
            }
            case "plasma" -> {
                // soft clouds of color drifting and swirling, the scale in periods of the sine across the picture
                double scale = 0.5 + (1 - size) * 1.0;
                double x = context.x * scale * context.aspectX;
                double y = context.y * scale * context.aspectY;
                double dx = x - scale / 2;
                double dy = y - scale / 2;
                double value = CycleSine.sin(x + cycle)
                        + CycleSine.sin(y * 0.9 - cycle * 0.7 + 0.16)
                        + CycleSine.sin((x + y) * 0.7 + cycle * 0.5)
                        + CycleSine.sin(Math.sqrt(dx * dx + dy * dy) * 1.3 - cycle);

                set(result, value / 8 + 0.5, 1);
            }
            case "ripple" -> {
                // rings spreading out from the middle
                double dx = (context.x - 0.5) * context.aspectX;
                double dy = (context.y - 0.5) * context.aspectY;
                double distance = Math.sqrt(dx * dx + dy * dy) * 1.4142135623730951;
                double ring = PixelMath.fract(distance * (0.8 + size * 3) - cycle);
                double tail = 0.15 + softness * 0.7;

                set(result, distance, PixelMath.smoothStep(0, 0.06, 1 - ring) * PixelMath.smoothStep(1 - tail, 1, ring));
            }
            case "shapes" -> {
                // an outline growing out of the middle and fading
                double x = (context.x - 0.5) * 2 * context.aspectX;
                double y = -(context.y - 0.5) * 2 * context.aspectY;
                double distance = shapeDistance(x, y, settings.getShape());
                double progress = PixelMath.fract(cycle);
                double width = 0.1 + size * 0.45;
                double edge = 0.03 + softness * 0.18;

                set(result, Math.floor(cycle) * 0.27 + distance * 0.25, PixelMath.band(Math.abs(distance - progress * 1.35), width / 2, edge) * (1 - progress * 0.55));
            }
            case "fire" -> {
                // flames rising from the bottom, embers flickering on a bar
                double offset = (context.seed % 997) * 1.37;

                if (!isMatrix(context)) {
                    double noise = PixelMath.fractalNoise(context.x * 3 * context.aspectX * Math.max(1, context.columns / 12.0) + offset, cycle * 2.2 + context.y * 3);
                    double heat = PixelMath.clamp01((noise - 0.22) * 1.8);

                    set(result, heat * 0.92, PixelMath.clamp01(heat * 1.35));
                } else {
                    double height = 1 - context.y;
                    double noise = PixelMath.fractalNoise(context.x * 3.2 * context.aspectX + offset, height * 2.6 * context.aspectY - cycle * 2.4);
                    double heat = PixelMath.clamp01(noise * 1.3 - height * (1.15 - size * 0.65) + 0.12);

                    set(result, heat, PixelMath.clamp01(heat * 1.7));
                }
            }
            // a pattern of a newer designer
            default -> set(result, 0, 0);
        }
    }

    private static void set(double[] result, double place, double level) {
        result[0] = place;
        result[1] = level;
    }

}
