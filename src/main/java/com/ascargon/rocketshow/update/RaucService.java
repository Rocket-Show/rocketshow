package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface RaucService {

    String getCurrentSlot();

    void installBundle(String url) throws Exception;

    void markGood() throws InterruptedException, IOException;

}
