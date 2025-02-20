package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(MidiSignal midiSignal, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination);

}
