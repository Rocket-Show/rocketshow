package com.ascargon.rocketshow.midi;

import org.freedesktop.gstreamer.Pipeline;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;

public class MidiCompositionFilePlayer {

    private MidiPlayer midiPlayer;

    public MidiCompositionFilePlayer(MidiRoutingService midiRoutingService, MidiCompositionFile midiCompositionFile, String path, Pipeline syncPipeline, MidiPlayer syncMidiPlayer) throws MidiUnavailableException, InvalidMidiDataException, IOException {
        midiPlayer = new MidiPlayer(midiRoutingService, path, syncPipeline, syncMidiPlayer, midiCompositionFile.getMidiRoutingList());
    }

    public void play() {
        midiPlayer.play();
    }

    public void pause() {
        midiPlayer.pause();
    }

    public void resume() {
        midiPlayer.play();
    }

    public void stop() {
        midiPlayer.stop();
    }

    public void seek(long positionMillis) {
        midiPlayer.seek(positionMillis);
    }

    public long getPositionMillis() {
        return midiPlayer.getPositionMillis();
    }

    public MidiPlayer getMidiPlayer() {
        return midiPlayer;
    }

}
