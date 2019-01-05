package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire the MIDI input to the mapped output.
 */
@Service
public class DefaultMidiRoutingService implements MidiRoutingService {

    private final static Logger logger = LoggerFactory.getLogger(MidiRouting.class);

    private final SettingsService settingsService;
    private final Midi2LightingConvertService midi2LightingConvertService;
    private final LightingService lightingService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final ActivityNotificationMidiService activityNotificationMidiService;

    private List<Midi2MonitorReceiver> midi2MonitorReceiverList;

    DefaultMidiRoutingService(SettingsService settingsService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService, ActivityNotificationMidiService activityNotificationMidiService) {
        this.settingsService = settingsService;
        this.midi2LightingConvertService = midi2LightingConvertService;
        this.lightingService = lightingService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.activityNotificationMidiService = activityNotificationMidiService;
    }

    private Receiver getReceiver(MidiRouting midiRouting) {
        if (midiRouting.getMidiDestination() == MidiSignal.MidiDestination.OUT_DEVICE) {
            // Connect the transmitter to the out device
            Midi2DeviceOutReceiver midi2DeviceOutReceiver = new Midi2DeviceOutReceiver(midiDeviceOutService);
            midi2DeviceOutReceiver.setMidiMapping(midiRouting.getMidiMapping());

            return midi2DeviceOutReceiver;
        } else if (midiRouting.getMidiDestination() == MidiSignal.MidiDestination.LIGHTING) {
            // Connect the transmitter to the LIGHTING receiver
            Midi2LightingReceiver midi2LightingReceiver = new Midi2LightingReceiver(midi2LightingConvertService, lightingService);
            midi2LightingReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2LightingReceiver.setMidi2LightingMapping(midiRouting.getMidi2LightingMapping());

            return midi2LightingReceiver;
        } else if (midiRouting.getMidiDestination() == MidiSignal.MidiDestination.REMOTE) {
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

        if (midi2MonitorReceiverList != null) {
            for (Midi2MonitorReceiver midi2MonitorReceiver : midi2MonitorReceiverList) {
                midi2MonitorReceiver.close();
            }
        }

        midi2MonitorReceiverList = new ArrayList<>();

        for (MidiRouting midiRouting : midiRoutingList) {
            Receiver receiver = getReceiver(midiRouting);

            if (receiver != null) {
                transmitter.setReceiver(receiver);
            }

            Midi2MonitorReceiver midi2MonitorReceiver = new Midi2MonitorReceiver(activityNotificationMidiService, midiRouting);
            transmitter.setReceiver(midi2MonitorReceiver);
            midi2MonitorReceiverList.add(midi2MonitorReceiver);
        }
    }

    public void sendSignal(MidiSignal midiSignal, List<MidiRouting> midiRoutingList) {
        // TODO Don't create a new receiver and close it for each signal but preserve it
        // maybe in the caller and pass the receiver here instead of the midiRoutingList?
        for (MidiRouting midiRouting : midiRoutingList) {
            Receiver receiver = getReceiver(midiRouting);

            if (receiver != null) {
                try {
                    receiver.send(midiSignal.getShortMessage(), -1);
                } catch (InvalidMidiDataException e) {
                    logger.error("Could not send MIDI signal", e);
                }
            }

            activityNotificationMidiService.notifyClients(midiSignal, MidiSignal.MidiDirection.OUT, null, midiRouting.getMidiDestination());

            receiver.close();
        }
    }

}
