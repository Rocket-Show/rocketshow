package com.ascargon.rocketshow.midi;

import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.*;
import java.io.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class MidiPlayer {

    private final static Logger logger = LoggerFactory.getLogger(MidiPlayer.class);

    private Sequencer sequencer;
    private boolean loop = false;
    private final Pipeline syncPipeline;
    private final MidiPlayer syncMidiPlayer;

    // Sync to a master Gstreamer pipeline or MIDI player, if available
    private Timer syncTimer;

    MidiPlayer(MidiRoutingService midiRoutingService, String path, Pipeline syncPipeline, MidiPlayer syncMidiPlayer, List<MidiRouting> midiRoutingList) throws MidiUnavailableException, IOException, InvalidMidiDataException {
        this.syncPipeline = syncPipeline;
        this.syncMidiPlayer = syncMidiPlayer;

        sequencer = MidiSystem.getSequencer(false);
        sequencer.open();

        InputStream is = new BufferedInputStream(new FileInputStream(new File(path)));
        sequencer.setSequence(is);

        midiRoutingService.connectTransmitter(sequencer.getTransmitter(), midiRoutingList);

        // Set the sequencer to looped, if necessary
        if (loop) {
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        }

        if (syncPipeline != null) {
            syncPipeline.getBus().connect((GstObject source, State old, State newState, State pending) -> {
                if (source.getTypeName().equals("GstPipeline")) {
                    if (newState == State.PLAYING && sequencer.isOpen()) {
                        sequencer.start();
                        startSyncTimer();
                    } else if (newState == State.PAUSED && sequencer.isOpen()) {
                        sequencer.stop();
                        stopSyncTimer();
                    }
                }
            });
        }

        logger.debug("File '" + path + "' loaded");
    }

    private void syncToMaster() throws MidiUnavailableException {
        if (!sequencer.isOpen()) {
            // We reached the end of the file -> also stop the sync
            stopSyncTimer();
        }

        Long masterPositionMicros = null;

        // The millis we allow to diff to the master, before syncing
        // TODO increase until stable
        long syncDifferenceThresholdMicros = 20000;

        // Sync to a master source, if available
        if (syncPipeline != null) {
            masterPositionMicros = syncPipeline.queryPosition(TimeUnit.MICROSECONDS);
        } else if (syncMidiPlayer != null) {
            masterPositionMicros = syncMidiPlayer.getPositionMicros();
        }

        if (masterPositionMicros >  0) {
            long slavePositionMicros = getPositionMicros();

            if (Math.abs(masterPositionMicros - slavePositionMicros) > syncDifferenceThresholdMicros && masterPositionMicros < sequencer.getMicrosecondLength()) {
                seekMicros(masterPositionMicros);
                logger.trace("Synced MIDI player with a difference of " + Math.abs(masterPositionMicros - slavePositionMicros) / 1000 + " milliseconds to the master (master position = " + masterPositionMicros + ", slave position = " + slavePositionMicros + ")...");
            }
        }
    }

    private void startSyncTimer() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    syncToMaster();
                } catch (MidiUnavailableException e) {
                    logger.error("Could not sync MIDI player to master", e);
                }
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
            // If we sync to a pipeline, we listen to events from the bus and don't use this function
            sequencer.start();

            if (syncMidiPlayer != null) {
                startSyncTimer();
            }
        }
    }

    public void pause() {
        if (syncPipeline != null && sequencer.isOpen()) {
            // If we sync to a pipeline, we listen to events from the bus and don't use this function
            sequencer.stop();
        }

        stopSyncTimer();
    }

    public void stop() {
        sequencer.close();
        sequencer.setMicrosecondPosition(0);
        stopSyncTimer();
    }

    public void seekMicros(long positionMicros) throws MidiUnavailableException {
        sequencer.setMicrosecondPosition(positionMicros);

        if (positionMicros < sequencer.getMicrosecondLength() && !sequencer.isOpen()) {
            // Open the sequencer again, because it might be closed due to a past seek
            // past the sequencer length.
            sequencer.open();
            startSyncTimer();
        }
    }

    public void seek(long positionMillis) throws MidiUnavailableException {
        seek(positionMillis * 1000);
    }

    public long getPositionMicros() {
        if (sequencer != null) {
            return sequencer.getMicrosecondPosition();
        }

        return 0;
    }

    public long getPositionMillis() {
        return getPositionMicros() / 1000;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

}
