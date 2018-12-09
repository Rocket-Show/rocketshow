package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface ActivityNotificationMidiService {

    void notifyClients(MidiSignal midiSignal, ActivityMidi.MidiSource midiSource);

}
