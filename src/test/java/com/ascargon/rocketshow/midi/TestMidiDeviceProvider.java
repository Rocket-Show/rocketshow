package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.sound.midi.spi.MidiDeviceProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * A MIDI interface (e.g. connected by USB) for the tests, offered to Java like the system's MIDI devices
 * (src/test/resources/META-INF/services).
 */
public class TestMidiDeviceProvider extends MidiDeviceProvider {

    public static final TestMidiDevice INTERFACE = new TestMidiDevice("Rocket Show test MIDI interface");

    @Override
    public MidiDevice.Info[] getDeviceInfo() {
        return new MidiDevice.Info[]{INTERFACE.getDeviceInfo()};
    }

    @Override
    public MidiDevice getDevice(MidiDevice.Info info) {
        if (info == INTERFACE.getDeviceInfo()) {
            return INTERFACE;
        }

        throw new IllegalArgumentException("Unknown MIDI device " + info);
    }

    public static class TestMidiDevice implements MidiDevice {

        private final Info info;
        private boolean open;

        // What the device got sent, what listens to the device
        public final List<MidiMessage> received = new CopyOnWriteArrayList<>();
        private final List<Receiver> listeners = new CopyOnWriteArrayList<>();

        // A cable from the MIDI out back to the MIDI in: what comes back of a message sent (null for nothing). No
        // cable, if null.
        public volatile UnaryOperator<MidiMessage> loopbackCable;

        // Like the MIDI devices of Java, the device keeps every receiver it handed out until it is closed
        private final List<Receiver> receivers = new CopyOnWriteArrayList<>();

        TestMidiDevice(String name) {
            info = new Info(name, "Rocket Show tests", "A MIDI interface for the tests", "1.0") {
            };
        }

        // Someone plays on the instrument connected to the interface
        public void play(MidiMessage midiMessage) {
            for (Receiver listener : listeners) {
                listener.send(midiMessage, -1);
            }
        }

        public void reset() {
            close();
            received.clear();
            loopbackCable = null;
        }

        @Override
        public Info getDeviceInfo() {
            return info;
        }

        @Override
        public void open() {
            open = true;
        }

        @Override
        public void close() {
            open = false;
            listeners.clear();
            receivers.clear();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public long getMicrosecondPosition() {
            return -1;
        }

        @Override
        public int getMaxReceivers() {
            return -1;
        }

        @Override
        public int getMaxTransmitters() {
            return -1;
        }

        @Override
        public Receiver getReceiver() {
            Receiver receiver = new Receiver() {
                @Override
                public void send(MidiMessage message, long timeStamp) {
                    received.add(message);

                    UnaryOperator<MidiMessage> cable = loopbackCable;
                    if (cable != null) {
                        MidiMessage back = cable.apply(message);
                        if (back != null) {
                            play(back);
                        }
                    }
                }

                @Override
                public void close() {
                    receivers.remove(this);
                }
            };
            receivers.add(receiver);
            return receiver;
        }

        @Override
        public List<Receiver> getReceivers() {
            return List.copyOf(receivers);
        }

        @Override
        public Transmitter getTransmitter() {
            return new Transmitter() {
                private Receiver receiver;

                @Override
                public void setReceiver(Receiver receiver) {
                    if (this.receiver != null) {
                        listeners.remove(this.receiver);
                    }
                    this.receiver = receiver;
                    listeners.add(receiver);
                }

                @Override
                public Receiver getReceiver() {
                    return receiver;
                }

                @Override
                public void close() {
                    listeners.remove(receiver);
                }
            };
        }

        @Override
        public List<Transmitter> getTransmitters() {
            return List.of();
        }

    }

}
