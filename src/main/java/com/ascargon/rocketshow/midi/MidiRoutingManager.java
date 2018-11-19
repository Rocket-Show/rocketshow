package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.dmx.Midi2DmxConvertService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

public class MidiRoutingManager {

    private final static Logger logger = LogManager.getLogger(MidiRouting.class);

    private SettingsService settingsService;

    private MidiRouting midiRouting;

    private Midi2DmxReceiver midi2DmxReceiver;
    private Midi2DeviceOutReceiver midi2DeviceOutReceiver;
    private Midi2RemoteReceiver midi2RemoteReceiver;

    private Transmitter transmitter;
    private Receiver receiver;

    public MidiRoutingManager(SettingsService settingsService, Midi2DmxConvertService midi2DmxConvertService, DmxService dmxService, MidiDeviceService midiDeviceService, Transmitter transmitter, MidiRouting midiRouting) throws MidiUnavailableException {
        this.settingsService = settingsService;

        this.midiRouting = midiRouting;
        this.transmitter = transmitter;

        if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.OUT_DEVICE) {
            // Connect the transmitter to the out device
            this.midi2DeviceOutReceiver = new Midi2DeviceOutReceiver(midiDeviceService);
            midi2DeviceOutReceiver.setMidiMapping(midiRouting.getMidiMapping());
            receiver = midi2DeviceOutReceiver;
        } else if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.DMX) {
            // Connect the transmitter to the DMX receiver
            this.midi2DmxReceiver = new Midi2DmxReceiver(midi2DmxConvertService, dmxService);
            midi2DmxReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2DmxReceiver.setMidi2DmxMapping(midiRouting.getMidi2DmxMapping());
            receiver = midi2DmxReceiver;
        } else if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.REMOTE) {
            // Connect the transmitter to the remote receiver
            this.midi2RemoteReceiver = new Midi2RemoteReceiver(settingsService);
            midi2RemoteReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2RemoteReceiver.setRemoteDeviceNameList(midiRouting.getRemoteDeviceIdList());
            receiver = midi2RemoteReceiver;
        }

        if(receiver != null && transmitter != null) {
            transmitter.setReceiver(receiver);
        }
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

}
