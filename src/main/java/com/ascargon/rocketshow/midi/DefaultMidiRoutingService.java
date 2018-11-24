package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.dmx.Midi2DmxConvertService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.util.List;

/**
 * Wire the MIDI input to the mapped output.
 */
@Service
public class DefaultMidiRoutingService implements MidiRoutingService {

    private final static Logger logger = LoggerFactory.getLogger(MidiRouting.class);

    private final SettingsService settingsService;
    private final Midi2DmxConvertService midi2DmxConvertService;
    private final DmxService dmxService;
    private final MidiDeviceOutService midiDeviceOutService;

    DefaultMidiRoutingService(SettingsService settingsService, Midi2DmxConvertService midi2DmxConvertService, DmxService dmxService, MidiDeviceOutService midiDeviceOutService) {
        this.settingsService = settingsService;
        this.midi2DmxConvertService = midi2DmxConvertService;
        this.dmxService = dmxService;
        this.midiDeviceOutService = midiDeviceOutService;
    }

    private Receiver getReceiver(MidiRouting midiRouting) {
        if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.OUT_DEVICE) {
            // Connect the transmitter to the out device
            Midi2DeviceOutReceiver midi2DeviceOutReceiver = new Midi2DeviceOutReceiver(midiDeviceOutService);
            midi2DeviceOutReceiver.setMidiMapping(midiRouting.getMidiMapping());

            return midi2DeviceOutReceiver;
        } else if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.DMX) {
            // Connect the transmitter to the DMX receiver
            Midi2DmxReceiver midi2DmxReceiver = new Midi2DmxReceiver(midi2DmxConvertService, dmxService);
            midi2DmxReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2DmxReceiver.setMidi2DmxMapping(midiRouting.getMidi2DmxMapping());

            return midi2DmxReceiver;
        } else if (midiRouting.getMidiDestination() == MidiRouting.MidiDestination.REMOTE) {
            // Connect the transmitter to the remote receiver
            Midi2RemoteReceiver midi2RemoteReceiver = new Midi2RemoteReceiver(settingsService);
            midi2RemoteReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2RemoteReceiver.setRemoteDeviceNameList(midiRouting.getRemoteDeviceIdList());

            return midi2RemoteReceiver;
        }

        return null;
    }

    public void connectTransmitter(Transmitter transmitter, List<MidiRouting> midiRoutingList) {
        if (transmitter == null) {
            return;
        }

        for (MidiRouting midiRouting : midiRoutingList) {
            Receiver receiver = getReceiver(midiRouting);

            if (receiver != null) {
                transmitter.setReceiver(receiver);
            }
        }
    }

    public void sendSignal(MidiSignal midiSignal, List<MidiRouting> midiRoutingList) {
        for (MidiRouting midiRouting : midiRoutingList) {
            Receiver receiver = getReceiver(midiRouting);

            if (receiver != null) {
                try {
                    receiver.send(midiSignal.getShortMessage(), -1);
                } catch (InvalidMidiDataException e) {
                    logger.error("Could not send MIDI signal", e);
                }
            }
        }
    }

}
