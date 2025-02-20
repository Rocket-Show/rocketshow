package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface MidiRouterFactory {

    MidiRouter getMidiRouter(List<MidiRouting> midiRoutingList);

}
