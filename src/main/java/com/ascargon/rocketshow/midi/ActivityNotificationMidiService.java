package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(ShortMessage shortMessage, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination);

}
