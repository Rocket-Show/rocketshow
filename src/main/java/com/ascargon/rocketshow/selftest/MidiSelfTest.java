package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends a burst of MIDI messages on the MIDI out device of the settings and listens on the MIDI in device for them,
 * e.g. through a loopback cable. During the whole self-test, the MIDI input goes only here: it can't start actions or
 * get routed to the MIDI out, DMX or other units.
 */
@Service
class MidiSelfTest implements SelfTestStep, Receiver {

    static final String MIDI_OUT = "midi-out";
    static final String MIDI_LOOPBACK = "midi-loopback";

    static final int BURST_SIZE = 200;

    // MIDI carries a message of 3 bytes in a millisecond, so even slow interfaces keep up
    private static final long PAUSE_MILLIS = 2;

    // How long the last messages may take to come back
    private static final long LOOPBACK_MILLIS = 1000;

    private final SettingsService settingsService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final MidiDeviceInService midiDeviceInService;

    // What the MIDI input received while listening
    private final List<ShortMessage> received = new CopyOnWriteArrayList<>();
    private volatile boolean listening = false;

    MidiSelfTest(SettingsService settingsService, MidiDeviceOutService midiDeviceOutService, MidiDeviceInService midiDeviceInService) {
        this.settingsService = settingsService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.midiDeviceInService = midiDeviceInService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(MIDI_OUT, MIDI_LOOPBACK);
    }

    @Override
    public void prepare() {
        midiDeviceInService.divert(this);
    }

    @Override
    public void restore() {
        listening = false;
        midiDeviceInService.divert(null);
    }

    // The MIDI input, during the self-test
    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        // Not the real-time messages (e.g. active sensing), which some interfaces send by themselves
        if (listening && midiMessage instanceof ShortMessage shortMessage && shortMessage.getStatus() < 0xF8) {
            received.add(shortMessage);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }

    // Notes, controllers and programs on every channel, with data bytes across their whole range
    static List<ShortMessage> burst() {
        List<ShortMessage> burst = new ArrayList<>();

        try {
            for (int i = 0; burst.size() < BURST_SIZE; i++) {
                int channel = i % 16;
                int note = (i * 7) % 128;

                burst.add(new ShortMessage(ShortMessage.NOTE_ON, channel, note, 1 + (i * 5) % 127));
                // Controllers 20 to 31 are undefined, so they don't change the instruments connected
                burst.add(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 20 + i % 12, (i * 13) % 128));
                burst.add(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, (i * 3) % 128, 0));
                burst.add(new ShortMessage(ShortMessage.NOTE_OFF, channel, note, 64));
            }
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }

        return burst;
    }

    private static String getName(MidiDevice midiDevice) {
        return midiDevice == null ? null : midiDevice.getName();
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        String outName = getName(settingsService.getSettings().getMidiOutDevice());
        String inName = getName(settingsService.getSettings().getMidiInDevice());

        if (!midiDeviceOutService.isConnected()) {
            String reason = outName == null ? "No MIDI out device in the settings" : "The MIDI out device is not connected";
            context.report(SelfTestCheck.skipped(MIDI_OUT, outName, reason));
            context.report(SelfTestCheck.skipped(MIDI_LOOPBACK, null, reason));
            return;
        }

        List<ShortMessage> burst = burst();
        received.clear();
        listening = true;

        try {
            int sent = 0;
            String sendError = null;

            for (ShortMessage shortMessage : burst) {
                if (context.isStopped()) {
                    context.report(SelfTestCheck.skipped(MIDI_OUT, outName, SelfTestContext.STOPPED));
                    context.report(SelfTestCheck.skipped(MIDI_LOOPBACK, null, SelfTestContext.STOPPED));
                    return;
                }

                try {
                    midiDeviceOutService.sendMessageChecked(shortMessage);
                } catch (Exception e) {
                    sendError = "Could not send message " + (sent + 1) + " of " + burst.size() + ": " + e.getMessage();
                    break;
                }

                sent++;
                Thread.sleep(PAUSE_MILLIS);
            }

            if (sendError == null) {
                context.report(SelfTestCheck.passed(MIDI_OUT, outName, sent + " messages sent"));
            } else {
                context.report(SelfTestCheck.failed(MIDI_OUT, outName, sendError));
            }

            if (!midiDeviceInService.isConnected()) {
                context.report(SelfTestCheck.skipped(MIDI_LOOPBACK, null, inName == null ? "No MIDI in device in the settings" : "The MIDI in device is not connected"));
                return;
            }

            // Until every message came back, and a moment longer for messages which come twice
            long end = System.currentTimeMillis() + LOOPBACK_MILLIS;
            while (received.size() < sent && System.currentTimeMillis() < end) {
                Thread.sleep(10);
            }
            Thread.sleep(50);
            listening = false;

            context.report(compare(burst.subList(0, sent), new ArrayList<>(received), "MIDI out " + outName + " → MIDI in " + inName));
        } finally {
            listening = false;
            allNotesOff();
        }
    }

    // Every message once, in order, unchanged
    static SelfTestCheck compare(List<ShortMessage> sent, List<ShortMessage> received, String target) {
        if (received.isEmpty()) {
            return SelfTestCheck.skipped(MIDI_LOOPBACK, target, "Nothing received, no loopback cable");
        }

        int matched = 0;
        int others = 0;
        int next = 0;

        for (ShortMessage shortMessage : received) {
            int index = indexOf(sent, shortMessage, next);
            if (index < 0) {
                others++;
            } else {
                matched++;
                next = index + 1;
            }
        }

        String details = matched + " of " + sent.size() + " messages received";

        if (matched == sent.size() && others == 0) {
            return SelfTestCheck.passed(MIDI_LOOPBACK, target, details);
        }

        return SelfTestCheck.failed(MIDI_LOOPBACK, target, details + (others == 0 ? "" : ", " + others + " other messages (changed, twice or out of order)"));
    }

    private static int indexOf(List<ShortMessage> messages, ShortMessage shortMessage, int from) {
        for (int i = from; i < messages.size(); i++) {
            if (Arrays.equals(messages.get(i).getMessage(), shortMessage.getMessage())) {
                return i;
            }
        }
        return -1;
    }

    private void allNotesOff() {
        for (int channel = 0; channel < 16; channel++) {
            try {
                midiDeviceOutService.sendMessage(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 123, 0));
            } catch (InvalidMidiDataException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
