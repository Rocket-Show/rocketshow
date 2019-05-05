package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface FileFilterService {

    boolean filterFile(String fileName);

}
