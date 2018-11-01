package com.ascargon.rocketshow.midi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.Pipeline;

import com.ascargon.rocketshow.Manager;

public class MidiPlayer {

    final static Logger logger = Logger.getLogger(MidiPlayer.class);

    private Sequencer sequencer;
    private List<MidiRouting> midiRoutingList;
    private boolean loop = false;

    public MidiPlayer(Manager manager, List<MidiRouting> midiRoutingList) throws MidiUnavailableException {
        this.midiRoutingList = midiRoutingList;
    }

    public void seek(long position) {
        sequencer.setMicrosecondPosition(position);
    }

    public void load(String path, Pipeline syncPipeline, MidiPlayer syncMidiPlayer) throws Exception {
        // TODO Sync to the GST pipeline or the midiplayer, if one of both is provided

        if (sequencer != null) {
            if (sequencer.isOpen()) {
                sequencer.close();
            }
        }

        sequencer = MidiSystem.getSequencer(false);
        sequencer.open();

        InputStream is = new BufferedInputStream(new FileInputStream(new File(path)));
        sequencer.setSequence(is);

        for (MidiRouting midiRouting : midiRoutingList) {
            midiRouting.setTransmitter(sequencer.getTransmitter());
        }

        // Set the sequencer to looped, if necessary
        if (loop) {
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        }

        logger.debug("File '" + path + "' loaded");
    }

    public static long getDuration(String path) throws Exception {
        long duration;

        Sequence sequence = MidiSystem.getSequence(new File(path));
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        sequencer.setSequence(sequence);
        duration = sequencer.getMicrosecondLength() / 1000;
        sequencer.close();

        return duration;
    }

    public void play() {
        logger.debug("Starting MIDI player from position " + Math.round(sequencer.getMicrosecondPosition() / 1000));
        sequencer.start();
    }

    public void pause() {
        sequencer.stop();
    }

    public void stop() {
        sequencer.stop();
        sequencer.setMicrosecondPosition(0);
    }

    public long getPositionMillis() {
        if (sequencer != null) {
            return sequencer.getMicrosecondPosition() / 1000;
        }

        return 0;
    }

    public void close() {
        sequencer.close();

        for (MidiRouting midiRouting : midiRoutingList) {
            midiRouting.close();
        }
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

}
