package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Route the MIDI input to the correct output based on a MIDI routing list.
 */
@Service
public class MidiRouter {

    private final SettingsService settingsService;
    private final Midi2LightingConvertService midi2LightingConvertService;
    private final LightingService lightingService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final ActivityNotificationMidiService activityNotificationMidiService;

    private Map<MidiRouting, Receiver> receiverList = new HashMap<>();

    private List<Midi2MonitorReceiver> midi2MonitorReceiverList = new ArrayList<>();

    public MidiRouter(SettingsService settingsService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService, ActivityNotificationMidiService activityNotificationMidiService, List<MidiRouting> midiRoutingList) {
        this.settingsService = settingsService;
        this.midi2LightingConvertService = midi2LightingConvertService;
        this.lightingService = lightingService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.activityNotificationMidiService = activityNotificationMidiService;

        // Create a receiver for each routing
        for (MidiRouting midirouting : midiRoutingList) {
            receiverList.put(midirouting, getReceiver(midirouting));
        }
    }

    // Get the correct receiver based on the routing
    private Receiver getReceiver(MidiRouting midiRouting) {
        if (midiRouting.getMidiDestination() == MidiDestination.OUT_DEVICE) {
            Midi2DeviceOutReceiver midi2DeviceOutReceiver = new Midi2DeviceOutReceiver(midiDeviceOutService);
            midi2DeviceOutReceiver.setMidiMapping(midiRouting.getMidiMapping());

            return midi2DeviceOutReceiver;
        } else if (midiRouting.getMidiDestination() == MidiDestination.LIGHTING) {
            Midi2LightingReceiver midi2LightingReceiver = new Midi2LightingReceiver(midi2LightingConvertService, lightingService);
            midi2LightingReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2LightingReceiver.setMidi2LightingMapping(midiRouting.getMidi2LightingMapping());

            return midi2LightingReceiver;
        } else if (midiRouting.getMidiDestination() == MidiDestination.REMOTE) {
            Midi2RemoteReceiver midi2RemoteReceiver = new Midi2RemoteReceiver(settingsService);
            midi2RemoteReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2RemoteReceiver.setRemoteDeviceNameList(midiRouting.getRemoteDeviceIdList());

            return midi2RemoteReceiver;
        }

        return null;
    }

    // Connect two transmitters, the processing one and another one to monitor the signals to all routings
    void connectTransmitter(Transmitter processingTransmitter, Transmitter monitoringTransmmitter) {
        for (Map.Entry<MidiRouting, Receiver> entry : receiverList.entrySet()) {
            // Connect the processing transmitter
            processingTransmitter.setReceiver(entry.getValue());

            // Connect the monitoring transmitter
            Midi2MonitorReceiver midi2MonitorReceiver = new Midi2MonitorReceiver(activityNotificationMidiService, entry.getKey());
            monitoringTransmmitter.setReceiver(midi2MonitorReceiver);
            midi2MonitorReceiverList.add(midi2MonitorReceiver);
        }
    }

    public void sendSignal(MidiMessage midiMessage) throws InvalidMidiDataException {
        // Send the signal to each receiver
        for (Map.Entry<MidiRouting, Receiver> entry : receiverList.entrySet()) {
            entry.getValue().send(midiMessage, -1);

            activityNotificationMidiService.notifyClients(midiMessage, MidiDirection.OUT, null, entry.getKey().getMidiDestination());
        }
    }

    public void close() {
        // Close all receivers
        for (Map.Entry<MidiRouting, Receiver> entry : receiverList.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().close();
            }
        }

        for (Midi2MonitorReceiver midi2MonitorReceiver : midi2MonitorReceiverList) {
            midi2MonitorReceiver.close();
        }
    }

}
