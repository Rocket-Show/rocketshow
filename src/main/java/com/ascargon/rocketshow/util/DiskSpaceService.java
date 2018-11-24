package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface DiskSpaceService {

    DiskSpace get() throws Exception;

}
