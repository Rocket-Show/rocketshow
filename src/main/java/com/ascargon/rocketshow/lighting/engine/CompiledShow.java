package com.ascargon.rocketshow.lighting.engine;

/**
 * What the compiler of the lighting engine made of a project: a show with its index, or why there is none.
 *
 * @param code     SHOW, UNREADABLE, UNSUPPORTED or TOO_LARGE
 * @param show     the compiled show (null without one)
 * @param index    which uuid has which number in the show (null without one)
 * @param message  the warnings of a show, or why there is none
 * @param features the feature bits the show needs, or the ones the engine lacks (UNSUPPORTED)
 */
public record CompiledShow(int code, byte[] show, ShowIndex index, String message, int features) {

    public static final int SHOW = 0;
    public static final int UNREADABLE = 1;
    public static final int UNSUPPORTED = 2;
    public static final int TOO_LARGE = 3;

    public boolean isShow() {
        return code == SHOW;
    }

}
