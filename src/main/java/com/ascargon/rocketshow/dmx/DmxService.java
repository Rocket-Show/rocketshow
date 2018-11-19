package com.ascargon.rocketshow.dmx;

import org.springframework.stereotype.Service;

@Service
public interface DmxService {

    void reset();

    void addDmxUniverse(DmxUniverse dmxUniverse);

    void removeDmxUniverse(DmxUniverse dmxUniverse);

    void close();

}
