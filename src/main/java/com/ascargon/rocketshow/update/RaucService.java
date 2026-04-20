package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

@Service
public interface RaucService {

    String getCurrentSlot();

    void installBundle(String url) throws Exception;

}
