package com.ascargon.rocketshow.util;

/**
 * Names of files coming from the web app or other units, e.g. of a composition, a media file or a designer project.
 */
public class FileNames {

    private FileNames() {
    }

    // A name must stay in its directory: no path separators, no parent directory
    public static String check(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid file name '" + name + "'");
        }

        return name;
    }

}
