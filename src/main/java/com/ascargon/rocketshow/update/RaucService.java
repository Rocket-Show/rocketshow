package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface RaucService {

    String getCurrentSlot();

    // The output of "rauc status", e.g. for the self-test
    String getStatus() throws Exception;

    void installBundle(String url) throws Exception;

    void markGood() throws InterruptedException, IOException;

}
