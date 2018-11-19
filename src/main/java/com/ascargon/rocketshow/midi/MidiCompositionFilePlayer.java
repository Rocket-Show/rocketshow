package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.dmx.Midi2DmxConvertService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freedesktop.gstreamer.Pipeline;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;

public class MidiCompositionFilePlayer {

    private final static Logger logger = LogManager.getLogger(MidiCompositionFilePlayer.class);

    private MidiCompositionFile midiCompositionFile;
    private String path;

    private MidiPlayer midiPlayer;

    public MidiCompositionFilePlayer(SettingsService settingsService, Midi2DmxConvertService midi2DmxConvertService, DmxService dmxService, MidiCompositionFile midiCompositionFile, MidiDeviceService midiDeviceService, String path, Pipeline syncPipeline, MidiPlayer syncMidiPlayer) throws MidiUnavailableException, InvalidMidiDataException, IOException {
        this.midiCompositionFile = midiCompositionFile;
        this.path = path;

        midiPlayer = new MidiPlayer(settingsService, midi2DmxConvertService, dmxService, midiDeviceService, path, syncPipeline, syncMidiPlayer, midiCompositionFile.getMidiRoutingList());
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
        return 0;
    }

    public MidiPlayer getMidiPlayer() {
        return midiPlayer;
    }

}
