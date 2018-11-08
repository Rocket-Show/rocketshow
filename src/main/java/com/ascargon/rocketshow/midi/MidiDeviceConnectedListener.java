package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiDevice;

public interface MidiDeviceConnectedListener {
	
	void deviceConnected(MidiDevice midiDevice);
	
}
