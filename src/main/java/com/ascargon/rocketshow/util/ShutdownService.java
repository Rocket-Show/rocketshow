package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface ShutdownService {

    void shutdown() throws InterruptedException, IOException;

}
