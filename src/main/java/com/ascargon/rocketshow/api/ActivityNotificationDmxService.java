package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.dmx.DmxUniverse;
import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationDmxService {

    void notifyClients(DmxUniverse dmxUniverse);

}
