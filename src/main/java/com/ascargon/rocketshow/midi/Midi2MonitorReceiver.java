package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.api.ActivityNotificationMidiService;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

public class Midi2MonitorReceiver implements Receiver {

    private final ActivityNotificationMidiService activityNotificationMidiService;
    private MidiRouting midiRouting;

    public Midi2MonitorReceiver(ActivityNotificationMidiService activityNotificationMidiService, MidiRouting midiRouting) {
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiRouting = midiRouting;
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        activityNotificationMidiService.notifyClients(midiMessage, MidiDirection.OUT, null, midiRouting.getMidiDestination());
    }

    @Override
    public void close() {
    }
}