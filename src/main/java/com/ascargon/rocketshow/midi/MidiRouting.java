package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.dmx.Midi2DmxMapping;
import com.ascargon.rocketshow.dmx.Midi2DmxReceiver;

/**
 * Defines, where to route the output of MIDI signals.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class MidiRouting {

    private final static Logger logger = Logger.getLogger(MidiRouting.class);

	public enum MidiDestination {
		OUT_DEVICE, DMX, REMOTE
	}

	private MidiDestination midiDestination = MidiDestination.OUT_DEVICE;

	private MidiMapping midiMapping = new MidiMapping();

	private Midi2DmxReceiver midi2DmxReceiver;
	private Midi2DmxMapping midi2DmxMapping = new Midi2DmxMapping();

	private Midi2RemoteReceiver midi2RemoteReceiver;

	// A list of remote device ids in case of destination type = REMOTE
	private List<String> remoteDeviceNameList = new ArrayList<>();

	private Midi2DeviceOutReceiver midi2DeviceOutReceiver;

	private Transmitter transmitter;
	private Receiver receiver;

	public MidiRouting() {
	}

	public void load(Manager manager) {
		midi2DmxReceiver = new Midi2DmxReceiver(manager);
		midi2DmxReceiver.setMidi2DmxMapping(midi2DmxMapping);
		midi2DmxReceiver.setMidiMapping(midiMapping);

		midi2RemoteReceiver = new Midi2RemoteReceiver();
		midi2RemoteReceiver.setRemoteDeviceNameList(remoteDeviceNameList);
		midi2RemoteReceiver.setMidiMapping(midiMapping);

		midi2DeviceOutReceiver = new Midi2DeviceOutReceiver(manager);
		midi2DeviceOutReceiver.setMidiMapping(midiMapping);

		refreshTransmitterConnection();
	}

	public void close() {
		if (midi2DmxReceiver != null) {
			midi2DmxReceiver.close();
		}
	}

	private void refreshTransmitterConnection() {
		if (midiDestination == MidiDestination.OUT_DEVICE) {
			// Connect the transmitter to the out device
			receiver = midi2DeviceOutReceiver;
		} else if (midiDestination == MidiDestination.DMX) {
			// Connect the transmitter to the DMX receiver
			receiver = midi2DmxReceiver;
		} else if (midiDestination == MidiDestination.REMOTE) {
			// Connect the transmitter to the remote receiver
			receiver = midi2RemoteReceiver;
		}

		if (transmitter == null) {
			return;
		}

		if (receiver == null) {
			return;
		}

		transmitter.setReceiver(receiver);
	}

	public void sendMidiMessage(MidiSignal midiSignal) {
		// Send a MIDI message to the current receiver
		if (receiver == null) {
			return;
		}

		try {
			receiver.send(midiSignal.getShortMessage(), -1);
		} catch (InvalidMidiDataException e) {
			logger.error("Could not send MIDI message", e);
		}
	}

    @SuppressWarnings("unused")
	public Midi2DmxMapping getMidi2DmxMapping() {
		return midi2DmxMapping;
	}

    @SuppressWarnings("unused")
	public void setMidi2DmxMapping(Midi2DmxMapping midi2DmxMapping) {
		this.midi2DmxMapping = midi2DmxMapping;

		if (midi2DmxReceiver != null) {
			midi2DmxReceiver.setMidi2DmxMapping(midi2DmxMapping);
		}
	}

	void setTransmitter(Transmitter transmitter) {
		this.transmitter = transmitter;
		refreshTransmitterConnection();
	}

	public MidiDestination getMidiDestination() {
		return midiDestination;
	}

	public void setMidiDestination(MidiDestination midiDestination) {
		this.midiDestination = midiDestination;
		refreshTransmitterConnection();
	}

	@XmlElement(name = "remoteDevice")
	@XmlElementWrapper(name = "remoteDeviceList")
    @SuppressWarnings("unused")
	public List<String> getRemoteDeviceIdList() {
		return remoteDeviceNameList;
	}

    @SuppressWarnings("unused")
	public void setRemoteDeviceIdList(List<String> remoteDeviceNameList) {
		this.remoteDeviceNameList = remoteDeviceNameList;

		if (midi2RemoteReceiver != null) {
			midi2RemoteReceiver.setRemoteDeviceNameList(remoteDeviceNameList);
		}
	}

	public MidiMapping getMidiMapping() {
		return midiMapping;
	}

	@XmlElement
	public void setMidiMapping(MidiMapping midiMapping) {
		this.midiMapping = midiMapping;

		if (midi2DmxReceiver != null) {
			midi2DmxReceiver.setMidiMapping(midiMapping);
		}

		if (midi2RemoteReceiver != null) {
			midi2RemoteReceiver.setMidiMapping(midiMapping);
		}

		if (midi2DeviceOutReceiver != null) {
			midi2DeviceOutReceiver.setMidiMapping(midiMapping);
		}
	}

}
