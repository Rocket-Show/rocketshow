package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.ActivityMidiSignal;
import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(ActivityMidiSignal activityMidiSignal, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination);

}
