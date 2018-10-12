package com.ascargon.rocketshow.dmx;

import java.io.IOException;

import javax.sound.midi.ShortMessage;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.dmx.Midi2DmxMapping.MappingType;
import com.ascargon.rocketshow.midi.MidiSignal;

public class Midi2DmxConverter {

	final static Logger logger = Logger.getLogger(Midi2DmxConverter.class);

	private DmxManager dmxManager;

	public Midi2DmxConverter(DmxManager dmxManager) {
		this.dmxManager = dmxManager;
	}

	private void mapSimple(MidiSignal midiSignal, DmxUniverse dmxUniverse) throws IOException {
		if (midiSignal.getCommand() == ShortMessage.NOTE_ON) {
			int valueTo = midiSignal.getVelocity() * 2;

			// Extend the last note to the max
			// TODO enable this feature by a mapping-setting
			if (valueTo == 254) {
				valueTo = 255;
			}

			dmxUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
			dmxManager.send();
		} else if (midiSignal.getCommand() == ShortMessage.NOTE_OFF) {
			int valueTo = 0;
			
			dmxUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
			dmxManager.send();
		}
	}

	private void mapExact(MidiSignal midiSignal, Midi2DmxMapping midi2DmxMapping, DmxUniverse dmxUniverse) throws IOException {
		// TODO
	}

	public void processMidiEvent(MidiSignal midiSignal, Midi2DmxMapping midi2DmxMapping, DmxUniverse dmxUniverse) throws IOException {
		// Map the MIDI event and send the appropriate DMX signal

		// Only react to NOTE_ON/NOTE_OFF events
		if (midiSignal.getCommand() != ShortMessage.NOTE_ON && midiSignal.getCommand() != ShortMessage.NOTE_OFF) {
			return;
		}

		if (midi2DmxMapping.getMappingType() == MappingType.SIMPLE) {
			mapSimple(midiSignal, dmxUniverse);
		} else if (midi2DmxMapping.getMappingType() == MappingType.EXACT) {
			mapExact(midiSignal, midi2DmxMapping, dmxUniverse);
		}
	}

	public DmxManager getDmxManager() {
		return dmxManager;
	}

	public void setDmxManager(DmxManager dmxManager) {
		this.dmxManager = dmxManager;
	}

}
