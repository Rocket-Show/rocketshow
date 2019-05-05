package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public class DefaultFileFilterService implements FileFilterService {

    public boolean filterFile(String fileName) {
        if(".DS_Store".equals(fileName)) {
            return true;
        }

        return false;
    }

}
