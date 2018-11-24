package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface RebootService {

    void reboot() throws InterruptedException, IOException;

}
