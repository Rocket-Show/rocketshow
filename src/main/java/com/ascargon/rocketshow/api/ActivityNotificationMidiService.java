package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(MidiMessage midiMessage, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination);

}
