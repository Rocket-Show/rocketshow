package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(MidiSignal midiSignal, MidiSignal.MidiDirection midiDirection, MidiSignal.MidiSource midiSource, MidiSignal.MidiDestination midiDestination);

}
