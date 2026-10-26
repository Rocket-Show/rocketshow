package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public class DefaultFileFilterService implements FileFilterService {

    public boolean filterFile(String fileName) {
        // Hidden files: e.g. .DS_Store and ._* of macOS, uploads and files being written, which are not complete yet
        return fileName.startsWith(".");
    }

}
