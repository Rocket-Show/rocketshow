package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.Transmitter;
import java.util.List;

@Service
public interface MidiRoutingService {

    void connectTransmitter(Transmitter transmitter, List<MidiRouting> midiRoutingList);

    void sendSignal(MidiSignal midiSignal, List<MidiRouting> midiRoutingList);

}
