package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import org.freedesktop.gstreamer.Pipeline;

public class MidiFile extends com.ascargon.rocketshow.composition.File {

    final static Logger logger = Logger.getLogger(MidiFile.class);

    public final static String MIDI_PATH = "midi/";

    private MidiPlayer midiPlayer;

    private List<MidiRouting> midiRoutingList;

    private Timer playTimer;

    public MidiFile() {
        List<MidiRouting> midiRoutingList = new ArrayList<MidiRouting>();
        setMidiRoutingList(midiRoutingList);
    }

    @XmlTransient
    public String getPath() {
        return Manager.BASE_PATH + MEDIA_PATH + MIDI_PATH + getName();
    }

    public void load(Pipeline syncPipeline, MidiPlayer syncMidiPlayer) throws Exception {
        logger.debug("Loading file '" + this.getName() + "...");

        if (midiPlayer == null) {
            midiPlayer = new MidiPlayer(this.getManager(), midiRoutingList);
        }

        midiPlayer.setLoop(this.isLoop());
        midiPlayer.load(this.getPath(), syncPipeline, syncMidiPlayer);

        for (MidiRouting midiRouting : midiRoutingList) {
            midiRouting.load(this.getManager());
        }
    }

    @XmlTransient
    public int getFullOffsetMillis() {
        return this.getOffsetMillis() + this.getManager().getSettings().getOffsetMillisMidi();
    }

    public void play() {
        if (midiPlayer == null) {
            logger.error("MIDI player not initialized for file '" + getPath() + "'");
            return;
        }

        if (this.getFullOffsetMillis() > 0) {
            logger.debug("Wait " + this.getFullOffsetMillis() + " milliseconds before starting the MIDI file '"
                    + this.getPath() + "'");

            playTimer = new Timer();
            playTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    playTimer.cancel();
                    playTimer = null;
                    midiPlayer.play();
                }
            }, this.getFullOffsetMillis());
        } else {
            midiPlayer.play();
        }
    }

    public void pause() {
        if (playTimer != null) {
            playTimer.cancel();
            playTimer = null;
        }

        if (midiPlayer == null) {
            logger.error("MIDI player not initialized for file '" + getPath() + "'");
            return;
        }

        midiPlayer.pause();
    }

    public void resume() {
        if (midiPlayer == null) {
            logger.error("MIDI player not initialized for file '" + getPath() + "'");
            return;
        }

        midiPlayer.play();
    }

    public void stop() throws Exception {
        if (playTimer != null) {
            playTimer.cancel();
            playTimer = null;
        }

        if (midiPlayer == null) {
            logger.error("MIDI player not initialized for file '" + getPath() + "'");
            return;
        }

        midiPlayer.stop();
    }

    public void seek(long positionMillis) {
        midiPlayer.seek(positionMillis);
    }

    public long getPositionMillis() {
        if (midiPlayer != null) {
            return midiPlayer.getPositionMillis();
        }

        return 0;
    }

    public void close() {
        if (playTimer != null) {
            playTimer.cancel();
            playTimer = null;
        }

        if (midiPlayer != null) {
            midiPlayer.close();
        }
    }

    public void setMidiRoutingList(List<MidiRouting> midiRoutingList) {
        for (MidiRouting midiRouting : midiRoutingList) {
            midiRouting.setMidiSource("MIDI file '" + getPath() + "'");
        }

        this.midiRoutingList = midiRoutingList;
    }

    @XmlElement(name = "midiRouting")
    @XmlElementWrapper(name = "midiRoutingList")
    public List<MidiRouting> getMidiRoutingList() {
        return midiRoutingList;
    }

    public FileType getType() {
        return FileType.MIDI;
    }

    public MidiPlayer getMidiPlayer() {
        return midiPlayer;
    }
}
