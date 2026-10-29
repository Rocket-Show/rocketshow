package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import javax.sound.midi.*;
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

    // Replaced as a whole when the routings change, so a message being sent meanwhile uses the old routings
    private volatile Map<MidiRouting, Receiver> receiverList = new HashMap<>();

    public MidiRouter(SettingsService settingsService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService, ActivityNotificationMidiService activityNotificationMidiService, List<MidiRouting> midiRoutingList) {
        this.settingsService = settingsService;
        this.midi2LightingConvertService = midi2LightingConvertService;
        this.lightingService = lightingService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.activityNotificationMidiService = activityNotificationMidiService;

        setMidiRoutingList(midiRoutingList);
    }

    // Route to other routings (e.g. after the settings changed) and close the receivers of the previous ones
    public synchronized void setMidiRoutingList(List<MidiRouting> midiRoutingList) {
        Map<MidiRouting, Receiver> newReceiverList = new HashMap<>();

        // Create a receiver for each routing
        for (MidiRouting midirouting : midiRoutingList) {
            // The global mapping applies to every routing. Routings of a composition get the composition's mapping
            // as parent instead, which uses the global mapping itself.
            if (midirouting.getMidiMapping() != null && midirouting.getMidiMapping().getParent() == null) {
                midirouting.getMidiMapping().setParent(settingsService.getSettings().getMidiMapping());
            }

            newReceiverList.put(midirouting, getReceiver(midirouting));
        }

        Map<MidiRouting, Receiver> oldReceiverList = receiverList;
        receiverList = newReceiverList;
        closeReceivers(oldReceiverList);
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
            midi2LightingReceiver.setUniverseUuid(midiRouting.getUniverseUuid());

            return midi2LightingReceiver;
        } else if (midiRouting.getMidiDestination() == MidiDestination.REMOTE) {
            Midi2RemoteReceiver midi2RemoteReceiver = new Midi2RemoteReceiver(settingsService);
            midi2RemoteReceiver.setMidiMapping(midiRouting.getMidiMapping());
            midi2RemoteReceiver.setRemoteDeviceNameList(midiRouting.getRemoteDeviceIdList());

            return midi2RemoteReceiver;
        }

        return null;
    }

    public void sendSignal(MidiMessage midiMessage, MidiSource midiSource) throws InvalidMidiDataException {
        if (midiMessage instanceof ShortMessage) {
            activityNotificationMidiService.notifyClients((ShortMessage) midiMessage, MidiDirection.IN, midiSource, null);
        }

        // Send the signal to each receiver. Each one gets its own copy, because the receivers map the message.
        for (Map.Entry<MidiRouting, Receiver> entry : receiverList.entrySet()) {
            MidiMessage routedMidiMessage = (MidiMessage) midiMessage.clone();

            entry.getValue().send(routedMidiMessage, -1);
            if (routedMidiMessage instanceof ShortMessage) {
                activityNotificationMidiService.notifyClients((ShortMessage) routedMidiMessage, MidiDirection.OUT, midiSource, entry.getKey().getMidiDestination());
            }
        }
    }

    private void closeReceivers(Map<MidiRouting, Receiver> receivers) {
        for (Receiver receiver : receivers.values()) {
            if (receiver != null) {
                receiver.close();
            }
        }
    }

    public synchronized void close() {
        // Close all receivers
        closeReceivers(receiverList);
    }

}
