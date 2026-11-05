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

    // where the dot of an orbit is at a moment of its period, from -1 to 1 on each axis (up being positive): the shapes
    // of the movements of the moving heads (see EffectPanTilt)
    private static double orbitX(double moment) {
        return CycleSine.sin(moment);
    }

    private static double orbitY(String shape, double moment) {
        return switch (shape == null ? "" : shape) {
            case "figure8" -> CycleSine.sin(moment * 2);
            case "line" -> CycleSine.sin(moment);
            case "wave" -> CycleSine.sin(moment * 3);
            default -> CycleSine.sin(moment + 0.25);
        };
    }

    // a curve of a wave at a moment of its period, between 0 and 1
    private static double waveCurve(String shape, double moment) {
        return switch (shape == null ? "" : shape) {
            case "triangle" -> PixelMath.triangle(moment);
            case "square" -> moment < 0.5 ? 1 : 0;
            case "sawtooth" -> moment;
            default -> 0.5 + 0.5 * CycleSine.sin(moment);
        };
    }

    // the place of a pixel counted from the end a stack piles up at, in pixels
    private static int stackIndex(PixelContext context, String direction) {
        return switch (direction == null ? "" : direction) {
            case "left" -> context.column;
            case "up" -> context.row;
            case "down" -> context.rows - 1 - context.row;
            default -> context.columns - 1 - context.column;
        };
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

    // Where a pixel shows a text: the column and the row of the text in the picture. The text moves a pixel at a time,
    // through the whole picture in a period, or stands aligned in it.
    private static int[] textPosition(PixelPatternSettings settings, double cycle, PixelContext context, PixelBitmap bitmap) {
        String scroll = settings.getScroll() == null || settings.getScroll().isEmpty() || "auto".equals(settings.getScroll()) ? "left" : settings.getScroll();
        int across = (int) Math.floor(PixelMath.fract(cycle) * (bitmap.getWidth() + context.columns));
        int down = (int) Math.floor(PixelMath.fract(cycle) * (bitmap.getHeight() + context.rows));
        int top = (int) Math.floor((context.rows - bitmap.getHeight()) / 2.0) - PixelMath.toInt(settings.getOffsetY());
        int x = switch (settings.getAlign() == null ? "" : settings.getAlign()) {
            case "left" -> context.column;
            case "right" -> context.column - (context.columns - bitmap.getWidth());
            default -> context.column - (int) Math.floor((context.columns - bitmap.getWidth()) / 2.0);
        };

        return switch (scroll) {
            case "right" -> new int[]{context.column - across + bitmap.getWidth(), context.row - top};
            case "up" -> new int[]{x, context.row + down - context.rows};
            case "down" -> new int[]{x, context.row - down + bitmap.getHeight()};
            case "none" -> new int[]{x, context.row - top};
            default -> new int[]{context.column + across - context.columns, context.row - top};
        };
    }

    // Where a pixel shows an image, from 0 to 1 on each axis (outside of it beyond): fitted into the picture whole
    // ('contain', with dark edges where it is narrower), filling it ('cover'), stretched over it or one pixel of the
    // image for each pixel ('pixels', in the middle), scrolling around
    private static double[] imagePosition(PixelPatternSettings settings, double cycle, PixelContext context, PixelBitmap bitmap) {
        double u = context.x;
        double v = context.y;
        double pictureRatio = context.aspectY > 0 ? context.aspectX / context.aspectY : context.columns;
        double imageRatio = (double) bitmap.getWidth() / bitmap.getHeight();

        switch (settings.getFit() == null ? "" : settings.getFit()) {
            case "cover" -> {
                if (imageRatio > pictureRatio) {
                    u = (u - 0.5) * (pictureRatio / imageRatio) + 0.5;
                } else {
                    v = (v - 0.5) * (imageRatio / pictureRatio) + 0.5;
                }
            }
            case "stretch" -> {
            }
            case "pixels" -> {
                u = (context.column - Math.floor((context.columns - bitmap.getWidth()) / 2.0) + 0.5) / bitmap.getWidth();
                v = (context.row - Math.floor((context.rows - bitmap.getHeight()) / 2.0) + 0.5) / bitmap.getHeight();
            }
            default -> {
                if (imageRatio > pictureRatio) {
                    v = (v - 0.5) * (imageRatio / pictureRatio) + 0.5;
                } else {
                    u = (u - 0.5) * (pictureRatio / imageRatio) + 0.5;
                }
            }
        }

        return switch (settings.getScroll() == null ? "" : settings.getScroll()) {
            case "left" -> new double[]{PixelMath.fract(u + cycle), v};
            case "right" -> new double[]{PixelMath.fract(u - cycle), v};
            case "up" -> new double[]{u, PixelMath.fract(v + cycle)};
            case "down" -> new double[]{u, PixelMath.fract(v - cycle)};
            default -> new double[]{u, v};
        };
    }

    // A pattern at a pixel and a moment: where it puts the pixel in the palette (any number, the palette repeats) into
    // result[0] and how bright the pixel is (between 0 and 1) into result[1]. An image showing its own colors gives the
    // color of the pixel into result[2] to result[4] (red, green and blue, between 0 and 1) in place of the palette, the
    // others -1 into result[2]. The moment is counted in periods of the effect since it started. The result has room for
    // five numbers.
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
            case "center" -> {
                // pulses running from the middle out to the ends (or the edges of a matrix), or in from them
                double dx = (context.x - 0.5) * 2 * context.aspectX;
                double dy = (context.y - 0.5) * 2 * context.aspectY;
                double distance = isMatrix(context) ? Math.min(1, Math.sqrt(dx * dx + dy * dy)) : Math.abs(context.x - 0.5) * 2;
                double position = "in".equals(direction) ? 1 - distance : distance;
                double width = Math.max(size, 0.03);
                double middle = PixelMath.fract(cycle) * (1 + width) - width / 2;
                double edge = Math.max(softness * width * 0.5, 1.0 / Math.max(context.columns, context.rows));

                set(result, Math.floor(cycle) * 0.25 + position * 0.3, PixelMath.band(Math.abs(position - middle), width / 2, edge));
            }
            case "stripes" -> {
                // evenly spaced stripes marching along, each in its own color
                double spacing = 0.08 + size * 0.5;
                double stripes = along(context, direction) / spacing - cycle + 0.25;
                double edge = Math.max(0.04 + softness * 0.2, 0.5 / (pixelsAlong(context, direction) * spacing));

                set(result, Math.floor(stripes) * 0.17, PixelMath.band(Math.abs(PixelMath.fract(stripes) - 0.5), 0.25, edge));
            }
            case "stack" -> {
                // pixels dropping in one by one and piling up at the far end, until all of them are there (each row or
                // column of a matrix on its own)
                int count = Math.max(2, pixelsAlong(context, direction));
                int index = Math.min(count - 1, Math.max(0, stackIndex(context, direction)));
                boolean vertical = "up".equals(direction) || "down".equals(direction);
                double lane = isMatrix(context) ? PixelMath.hash(vertical ? context.column : 0, vertical ? 0 : context.row, 9) : 0;
                double progress = PixelMath.fract(cycle + lane);
                double steps = progress * (count + 1);
                double step = Math.floor(steps);
                double place = (double) index / (count - 1);

                if (step >= count || index < step) {
                    set(result, place, 1);
                } else {
                    // the one dropping in, from the near end down to the pile
                    double falling = count - 1 - (steps - step) * (count - 1 - step);

                    set(result, place, index == Math.floor(falling + 0.5) ? 1 : 0);
                }
            }
            case "sweep" -> {
                // a beam turning around the middle with a trail behind it, like a radar
                double turn = ArcTangent.atan2((context.y - 0.5) * context.aspectY, (context.x - 0.5) * context.aspectX) / FULL_CYCLE;
                double behind = PixelMath.fract(cycle - ("ccw".equals(direction) ? -turn : turn));
                double trail = 0.12 + size * 0.7;
                double left = 1 - behind / trail;

                set(result, 1 - (behind / trail) * 0.8, behind < trail ? left * left : 0);
            }
            case "spiral" -> {
                // arms of a spiral turning and seeming to pull inwards
                double dx = (context.x - 0.5) * 2 * context.aspectX;
                double dy = (context.y - 0.5) * 2 * context.aspectY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                double turn = ArcTangent.atan2(dy, dx) / FULL_CYCLE;
                double arms = Math.floor(1.5 + size * 5);
                double arm = PixelMath.fract(turn * arms + distance * 1.4 + ("ccw".equals(direction) ? cycle : -cycle));

                set(result, distance * 0.7, PixelMath.band(Math.abs(arm - 0.5), 0.25, 0.04 + softness * 0.22));
            }
            case "checker" -> {
                // squares swapping every half period, fading over to the other color where it is soft
                double cells = Math.floor(1.5 + size * 6);
                double odd = (Math.floor(context.column / cells) + Math.floor(context.row / cells)) % 2;
                double half = PixelMath.fract(cycle) < 0.5 ? 0 : 1;
                double fade = PixelMath.smoothStep(0, softness * 0.5 + 0.001, PixelMath.fract(cycle * 2));

                set(result, odd, odd == half ? fade : 1 - fade);
            }
            case "rain" -> {
                // drops falling down the columns, each column at a speed of its own
                int column = context.column + context.seed;
                double speed = 0.6 + PixelMath.hash(column, 0, 3) * 0.9;
                double tail = 0.15 + size * 0.6;
                double brightest = 0;

                for (int drop = 0; drop < 2; drop++) {
                    double behind = PixelMath.fract(cycle * speed + PixelMath.hash(column, drop, 5)) * (1 + tail) - context.y;

                    if (behind >= 0 && behind < tail) {
                        brightest = Math.max(brightest, 1 - behind / tail);
                    }
                }

                set(result, brightest, brightest * brightest);
            }
            case "tunnel" -> {
                // frames flying outwards, as if driving through a tunnel: the deeper the closer together
                double x = (context.x - 0.5) * 2 * context.aspectX;
                double y = -(context.y - 0.5) * 2 * context.aspectY;
                double distance = shapeDistance(x, y, settings.getShape());
                double depth = (0.25 + size * 0.75) / (distance + 0.15) + cycle;
                double frame = PixelMath.band(Math.abs(PixelMath.fract(depth) - 0.5), 0.22, 0.05 + softness * 0.3);

                set(result, PixelMath.fract(Math.floor(depth) * 0.27), frame * PixelMath.clamp01(distance * 1.6 + 0.15));
            }
            case "orbit" -> {
                // a glowing dot moving along the shape of a movement of the moving heads, with a trail
                double radius = 0.08 + size * 0.25;
                double gap = 0.004 + softness * 0.03;
                double level = 0;
                double place = 0;

                for (int behind = 0; behind < 7; behind++) {
                    double moment = PixelMath.fract(cycle - behind * gap);
                    double dx = (context.x - (0.5 + 0.36 * orbitX(moment))) * context.aspectX;
                    double dy = (context.y - (0.5 - 0.36 * orbitY(settings.getShape(), moment))) * context.aspectY;
                    double distance = Math.sqrt(dx * dx + dy * dy) / radius;
                    double glow = PixelMath.clamp01(1 - distance * distance * 0.5);
                    double weight = glow * glow * (1 - behind / 7.0);

                    if (weight > level) {
                        level = weight;
                        place = moment;
                    }
                }

                set(result, place, level);
            }
            case "wave" -> {
                // a matrix filled up to a curve running along, a bar showing the curve itself
                double moment = PixelMath.fract(along(context, "left".equals(direction) ? "left" : "right") * (0.4 + (1 - size) * 2.2) - cycle);
                double value = 0.08 + 0.84 * waveCurve(settings.getShape(), moment);

                if (!isMatrix(context)) {
                    set(result, value, value);
                } else {
                    double height = 1 - context.y;
                    double edge = 0.5 / context.rows;

                    set(result, height, 1 - PixelMath.smoothStep(value - edge, value + edge, height));
                }
            }
            case "text" -> {
                // a text of a pixel for each pixel, scrolling through the picture
                PixelBitmap bitmap = settings.getBitmap();

                if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    set(result, 0, 0);
                    return;
                }

                int[] position = textPosition(settings, cycle, context, bitmap);
                int value = bitmap.value(bitmap.frame(0), position[0], position[1], 0);

                if (value <= 0) {
                    set(result, 0, 0);
                    return;
                }

                double place = switch (settings.getTextColors() == null ? "" : settings.getTextColors()) {
                    case "letters" -> {
                        int letter = 0;

                        for (Integer start : bitmap.getLetterStarts()) {
                            if (start != null && start <= position[0]) {
                                letter++;
                            }
                        }

                        yield letter * 0.17;
                    }
                    case "one" -> 0;
                    default -> (double) position[0] / bitmap.getWidth();
                };

                set(result, place, value / 255.0);
            }
            case "image" -> {
                // an image or an animation, fitted into the picture
                PixelBitmap bitmap = settings.getBitmap();

                if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    set(result, 0, 0);
                    return;
                }

                double[] position = imagePosition(settings, cycle, context, bitmap);

                if (position[0] < 0 || position[0] >= 1 || position[1] < 0 || position[1] >= 1) {
                    set(result, 0, 0);
                    return;
                }

                byte[] frame = bitmap.frame(bitmap.frameAt(PixelMath.fract(cycle)));
                int x = Math.min((int) Math.floor(position[0] * bitmap.getWidth()), bitmap.getWidth() - 1);
                int y = Math.min((int) Math.floor(position[1] * bitmap.getHeight()), bitmap.getHeight() - 1);
                int red = bitmap.value(frame, x, y, 0);
                int green = bitmap.getChannels() == 3 ? bitmap.value(frame, x, y, 1) : red;
                int blue = bitmap.getChannels() == 3 ? bitmap.value(frame, x, y, 2) : red;

                if ("palette".equals(settings.getImageColors())) {
                    // how bright it is, through the palette
                    set(result, (red * 299 + green * 587 + blue * 114) / 255000.0, 1);
                } else {
                    set(result, 0, 1);
                    result[2] = red / 255.0;
                    result[3] = green / 255.0;
                    result[4] = blue / 255.0;
                }
            }
            // a pattern of a newer designer
            default -> set(result, 0, 0);
        }
    }

    // a place in the palette and a brightness, the color taken from the palette
    private static void set(double[] result, double place, double level) {
        result[0] = place;
        result[1] = level;
        result[2] = -1;
    }

}
