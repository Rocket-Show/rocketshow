package com.ascargon.rocketshow.midi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;

import org.freedesktop.gstreamer.State;

public class MidiPlayer {

    private final static Logger logger = Logger.getLogger(MidiPlayer.class);

    private Sequencer sequencer;
    private List<MidiRouting> midiRoutingList;
    private boolean loop = false;
    private Pipeline syncPipeline;
    private MidiPlayer syncMidiPlayer;

    // Sync to a master, if available
    private Timer syncTimer;

    MidiPlayer(List<MidiRouting> midiRoutingList) {
        this.midiRoutingList = midiRoutingList;
    }

    public void seek(long positionMillis) {
        sequencer.setMicrosecondPosition(positionMillis * 1000);
    }

    private void syncToMaster() {
        Long masterPositionMillis = null;

        // The millis we allow to diff to the master, before syncing
        long syncDifferenceThresholdMillis = 10;

        // Sync to a master source, if available
        if (syncPipeline != null) {
            masterPositionMillis = syncPipeline.queryPosition(TimeUnit.MILLISECONDS);
        } else if (syncMidiPlayer != null) {
            masterPositionMillis = syncMidiPlayer.getPositionMillis();
        }

        if (masterPositionMillis != null) {
            if (Math.abs(masterPositionMillis - getPositionMillis()) > syncDifferenceThresholdMillis && masterPositionMillis < sequencer.getMicrosecondLength() / 1000) {
                logger.trace("Syncing MIDI player with a difference of " + Math.abs(masterPositionMillis - getPositionMillis()) + " to the master (master position = " + masterPositionMillis + ", slave position = " + getPositionMillis() + ")...");
                seek(masterPositionMillis);
            }
        }
    }

    private void startSyncTimer() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                syncToMaster();
            }
        };

        syncTimer = new Timer();

        // TODO Make the sync interval configurable
        syncTimer.schedule(timerTask, 0, 250);
    }

    private void stopSyncTimer() {
        if (syncTimer != null) {
            syncTimer.cancel();
        }

        syncTimer = null;
    }

    public void load(String path, Pipeline syncPipeline, MidiPlayer syncMidiPlayer) throws Exception {
        // TODO Sync to the GST pipeline or the midiplayer, if one of both is provided
        this.syncPipeline = syncPipeline;
        this.syncMidiPlayer = syncMidiPlayer;

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

        if (syncPipeline != null) {
            syncPipeline.getBus().connect((GstObject source, State old, State newState, State pending) -> {
                if (source.getTypeName().equals("GstPipeline")) {
                    if (newState == State.PLAYING) {
                        sequencer.start();
                        startSyncTimer();
                    } else if (newState == State.PAUSED) {
                        sequencer.stop();
                        stopSyncTimer();
                    }
                }
            });
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
        logger.debug("Starting MIDI player");

        if (syncPipeline == null) {
            sequencer.start();

            if (syncMidiPlayer != null) {
                startSyncTimer();
            }
        }
    }

    public void pause() {
        if (syncPipeline != null) {
            sequencer.stop();
        }

        stopSyncTimer();
    }

    public void stop() {
        sequencer.stop();
        sequencer.setMicrosecondPosition(0);
        stopSyncTimer();
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

        stopSyncTimer();
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

}
