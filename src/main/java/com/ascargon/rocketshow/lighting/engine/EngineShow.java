package com.ascargon.rocketshow.lighting.engine;

import run.endive.runtime.Memory;
import run.endive.wasm.types.Value;

/**
 * An engine of the lighting engine playing a compiled show: the inputs of the host, each with its moment where it has
 * one, and a frame worked out for a moment. The moments are whole microseconds of a clock of the host, the positions
 * microseconds of the timeline. Close it when it is done with, to give its memory back to the module.
 * <p>
 * The calls go one at a time through the lock of the module (see LightingEngine).
 */
public class EngineShow implements AutoCloseable {

    // bits of the flags of play_timeline: played as the show (every track), not as a preview, and the composition
    // played in between, which follows a tempo from outside
    public static final int TIMELINE_PLAYER = 1;
    public static final int TIMELINE_DEFAULT_COMPOSITION = 2;

    public static final int UNIVERSE_SIZE = 512;

    private final LightingEngine engine;
    private final ShowIndex index;
    private int handle;

    // memory in the module for a frame of audio, kept for all of them
    private int audioPointer;
    private int audioLength;

    EngineShow(LightingEngine engine, int handle, ShowIndex index) {
        this.engine = engine;
        this.handle = handle;
        this.index = index;
    }

    public ShowIndex getIndex() {
        return index;
    }

    public boolean isClosed() {
        return handle == 0;
    }

    private static long flag(boolean value) {
        return value ? 1 : 0;
    }

    private static long f32(double value) {
        return Value.floatToLong((float) value);
    }

    private void check() {
        if (handle == 0) {
            throw new IllegalStateException("The engine is closed");
        }
    }

    // ---- what plays ----

    // play the timeline of a composition (by its number), see the TIMELINE_ flags
    public void playTimeline(int composition, int flags) {
        synchronized (engine) {
            check();
            engine.playTimeline.apply(handle, composition, flags);
        }
    }

    // preview what is selected: the selected preset alone (solo), or else the selected scenes
    public void playPreview(boolean solo) {
        synchronized (engine) {
            check();
            engine.playPreview.apply(handle, flag(solo));
        }
    }

    // play the scenes picked by hand, from now on
    public void playLive(long nowMicros) {
        synchronized (engine) {
            check();
            engine.playLive.apply(handle, nowMicros);
        }
    }

    public void stop() {
        synchronized (engine) {
            check();
            engine.stop.apply(handle);
        }
    }

    // the preset selected for the preview and the step being edited in it (-1: none)
    public void selectPreset(int preset, int step) {
        synchronized (engine) {
            check();
            engine.selectPreset.apply(handle, preset, step);
        }
    }

    // whether the steps of the previewed presets run (from a position on) or hold at the step being edited
    public void setStepPreview(boolean running, long startMicros) {
        synchronized (engine) {
            check();
            engine.setStepPreview.apply(handle, flag(running), startMicros);
        }
    }

    public void selectScene(int scene, boolean selected) {
        synchronized (engine) {
            check();
            engine.selectScene.apply(handle, scene, flag(selected));
        }
    }

    public void clearScenes() {
        synchronized (engine) {
            check();
            engine.clearScenes.apply(handle);
        }
    }

    // ---- the timeline ----

    // where the timeline (or the clock of the preview) is at a moment, and whether it runs on from there
    public void setPosition(long nowMicros, long positionMicros, boolean running) {
        synchronized (engine) {
            check();
            engine.setPosition.apply(handle, nowMicros, positionMicros, flag(running));
        }
    }

    // the timeline holds at a hold point until go
    public void hold(long nowMicros, long positionMicros) {
        synchronized (engine) {
            check();
            engine.hold.apply(handle, nowMicros, positionMicros);
        }
    }

    public void go(long nowMicros) {
        synchronized (engine) {
            check();
            engine.go.apply(handle, nowMicros);
        }
    }

    // the position jumped: the holds from there on weren't gone through anymore
    public void forgetHolds(long fromPositionMicros) {
        synchronized (engine) {
            check();
            engine.forgetHolds.apply(handle, fromPositionMicros);
        }
    }

    // a hold the timeline went through: its position and the time held there
    public void addHold(long positionMicros, long heldMicros) {
        synchronized (engine) {
            check();
            engine.addHold.apply(handle, positionMicros, heldMicros);
        }
    }

    // whether the timeline holds at the last hold added (for as long already as that hold says)
    public void setHolding(long nowMicros, boolean holding) {
        synchronized (engine) {
            check();
            engine.setHolding.apply(handle, nowMicros, flag(holding));
        }
    }

    // ---- the tempo ----

    // the tempo from outside in beats per minute (0 or less: none, the programmed one)
    public void setTempo(long nowMicros, double beatsPerMinute) {
        synchronized (engine) {
            check();
            engine.setTempo.apply(handle, nowMicros, f32(beatsPerMinute));
        }
    }

    // a beat falls on a moment (a beat tapped)
    public void tap(long nowMicros) {
        synchronized (engine) {
            check();
            engine.tap.apply(handle, nowMicros);
        }
    }

    // the first beat of a bar falls on a moment
    public void beatOne(long nowMicros, boolean jump) {
        synchronized (engine) {
            check();
            engine.beatOne.apply(handle, nowMicros, flag(jump));
        }
    }

    // where the beats are at a moment, at once
    public void setBeats(long nowMicros, double beats) {
        synchronized (engine) {
            check();
            engine.setBeats.apply(handle, nowMicros, f32(beats));
        }
    }

    // ---- the scenes picked by hand ----

    // a scene is picked by hand (it fades in) or let go (it fades out)
    public void liveScene(long nowMicros, int scene, boolean on) {
        synchronized (engine) {
            check();
            engine.liveScene.apply(handle, nowMicros, scene, flag(on));
        }
    }

    // a scene is flashed (on at once, on top of the others) or let go
    public void liveFlash(long nowMicros, int scene, boolean on) {
        synchronized (engine) {
            check();
            engine.liveFlash.apply(handle, nowMicros, scene, flag(on));
        }
    }

    public void liveDimmer(int scene, double dimmer) {
        synchronized (engine) {
            check();
            engine.liveDimmer.apply(handle, scene, f32(dimmer));
        }
    }

    // the speed master of the scenes picked by hand (1: as written)
    public void liveSpeed(long nowMicros, double speed) {
        synchronized (engine) {
            check();
            engine.liveSpeed.apply(handle, nowMicros, f32(speed));
        }
    }

    // turn all scenes picked by hand off at once and start their clock anew
    public void liveReset() {
        synchronized (engine) {
            check();
            engine.liveReset.apply(handle);
        }
    }

    // whether no scene picked by hand plays anymore: none is on, flashed or still fades out
    public boolean isLiveIdle(long nowMicros) {
        synchronized (engine) {
            check();
            return engine.liveIdle.apply(handle, nowMicros)[0] != 0;
        }
    }

    // ---- the rest of the inputs ----

    // the master dimmer of the host (0 to 1), on top of the one of the project
    public void setMasterDimmer(double dimmer) {
        synchronized (engine) {
            check();
            engine.setMasterDimmer.apply(handle, f32(dimmer));
        }
    }

    public void setBlackout(boolean blackout) {
        synchronized (engine) {
            check();
            engine.setBlackout.apply(handle, flag(blackout));
        }
    }

    // a fixture (by its number) being identified blinks at full white
    public void identify(int fixture, boolean on) {
        synchronized (engine) {
            check();
            engine.identify.apply(handle, fixture, flag(on));
        }
    }

    public void resetFixture(int fixture, boolean on) {
        synchronized (engine) {
            check();
            engine.resetFixture.apply(handle, fixture, flag(on));
        }
    }

    public void lampOn(int fixture, boolean on) {
        synchronized (engine) {
            check();
            engine.lampOn.apply(handle, fixture, flag(on));
        }
    }

    // hold a DMX channel (from 0) of a universe (by its number) at a value (below 0: let go)
    public void setOverride(int universe, int channel, int value) {
        synchronized (engine) {
            check();
            engine.setOverride.apply(handle, universe, channel, value);
        }
    }

    public void clearOverrides() {
        synchronized (engine) {
            check();
            engine.clearOverrides.apply(handle);
        }
    }

    // a frame of the analysis of the audio playing (21 values in dB, see AudioAnalysisService), for the 20 ms from
    // index * 20 ms of the position played
    public void audioFrame(long index, double[] values) {
        synchronized (engine) {
            check();
            int length = values.length * Float.BYTES;

            if (length > audioLength) {
                if (audioPointer != 0) {
                    engine.free(audioPointer, audioLength);
                }

                audioPointer = (int) engine.alloc.apply(length)[0];
                audioLength = length;
            }

            Memory memory = engine.memory();

            for (int value = 0; value < values.length; value++) {
                memory.writeF32(audioPointer + value * Float.BYTES, (float) values[value]);
            }

            engine.audioFrame.apply(handle, index, audioPointer, values.length);
        }
    }

    // forget the frames of audio handed over (the audio stopped, or another one plays)
    public void clearAudio() {
        synchronized (engine) {
            check();
            engine.clearAudio.apply(handle);
        }
    }

    // ---- a frame ----

    // work out a frame for a moment: the values of every channel of every part and the universes
    public void render(long nowMicros) {
        synchronized (engine) {
            check();
            engine.render.apply(handle, nowMicros);
        }
    }

    // the number of universes of the show (512 channels each)
    public int getUniverseCount() {
        synchronized (engine) {
            check();
            return (int) (engine.universesLength.apply(handle)[0] / UNIVERSE_SIZE);
        }
    }

    /**
     * Copy the universes of the last frame into the passed buffer (512 bytes for each universe of the show, one after
     * the other, as many as fit), without allocating.
     */
    public void readUniverses(byte[] universes) {
        synchronized (engine) {
            check();
            int pointer = (int) engine.universesPointer.apply(handle)[0];
            int length = Math.min(universes.length, (int) engine.universesLength.apply(handle)[0]);
            Memory memory = engine.memory();
            int whole = length - length % Long.BYTES;

            for (int offset = 0; offset < whole; offset += Long.BYTES) {
                long word = memory.readLong(pointer + offset);

                // little endian, like the memory of WebAssembly
                for (int shift = 0; shift < Long.BYTES; shift++) {
                    universes[offset + shift] = (byte) (word >>> (shift * 8));
                }
            }

            for (int offset = whole; offset < length; offset++) {
                universes[offset] = memory.read(pointer + offset);
            }
        }
    }

    /**
     * The values of the last frame, as the bits of single floats, one for each channel of each part (in the order of
     * the index), and for each a word of flags of which bit 0 says whether the channel has a value at all. Both
     * arrays have to be of getValueCount() length at least.
     */
    public void readValues(int[] valueBits, int[] flags) {
        synchronized (engine) {
            check();
            int count = (int) engine.valuesLength.apply(handle)[0];
            int values = (int) engine.valuesPointer.apply(handle)[0];
            int flagWords = (int) engine.flagsPointer.apply(handle)[0];
            Memory memory = engine.memory();

            for (int value = 0; value < count; value++) {
                valueBits[value] = memory.readInt(values + value * Integer.BYTES);
                flags[value] = memory.readInt(flagWords + value * Integer.BYTES);
            }
        }
    }

    public int getValueCount() {
        synchronized (engine) {
            check();
            return (int) engine.valuesLength.apply(handle)[0];
        }
    }

    @Override
    public void close() {
        synchronized (engine) {
            if (handle == 0) {
                return;
            }

            if (audioPointer != 0) {
                engine.free(audioPointer, audioLength);
                audioPointer = 0;
            }

            engine.engineFree.apply(handle);
            handle = 0;
        }
    }

}
