package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.RemoteDevice;
import com.ascargon.rocketshow.SettingsService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Receive MIDI messages and send them to remote devices.
 *
 * @author Moritz A. Vieli
 */
class Midi2RemoteReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(Midi2RemoteReceiver.class);

    private final SettingsService settingsService;

    private MidiMapping midiMapping;

    private List<String> remoteDeviceNameList = new ArrayList<>();

    Midi2RemoteReceiver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);
        MidiMapper.processMidiEvent(midiSignal, midiMapping);

        String apiUrl = "midi/send-message?command=" + midiSignal.getCommand() + "&channel=" + midiSignal.getChannel()
                + "&note=" + midiSignal.getNote() + "&velocity" + midiSignal.getVelocity();

        for (String name : remoteDeviceNameList) {
            RemoteDevice remoteDevice = settingsService.getRemoteDeviceByName(name);

            if (remoteDevice == null) {
                logger.warn("No remote device could be found in the settings with name " + name);
            } else {
                try {
                    remoteDevice.doPost(apiUrl);
                } catch (Exception e) {
                    logger.error("Could not send MIDI message to remote device '" + remoteDevice.getHost() + "' ("
                            + name + ")", e);
                }
            }
        }
    }

    @Override
    public void close() {
        // Nothing to do at the moment
    }

    void setRemoteDeviceNameList(List<String> remoteDeviceNameList) {
        this.remoteDeviceNameList = remoteDeviceNameList;
    }

    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

}
