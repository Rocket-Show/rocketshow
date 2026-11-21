package com.ascargon.rocketshow.lighting.engine;

import run.endive.compiler.InterpreterFallback;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.runtime.Memory;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * The shared lighting engine (Rocket-Show/lighting-engine, see docs/lighting-engine.md) inside the JVM: its
 * engine.wasm, the same file the designer loads, run by the WebAssembly runtime Endive. The module is compiled to JVM
 * bytecode when it is loaded, which the JVM then optimises like any Java code.
 * <p>
 * This class is the boundary of engine.wasm as it is (the functions starting with le_): the compiler, which turns the
 * JSON of a project into a show, and the engines playing a show (see EngineShow). Only integers, single floats and byte
 * ranges cross it. The module runs one call at a time: every call goes through this object's lock. A fault of the
 * engine (a trap) comes out as a RuntimeException; the module may be broken afterwards, so it is loaded anew then.
 * <p>
 * engine.wasm is not in the repository (testing/build-lighting-engine.sh builds it into the resources): without it,
 * isAvailable() is false and Rocket Show plays with its own engine only.
 */
public class LightingEngine {

    // where the build packs engine.wasm, and the revision of the engine it was built from
    public static final String RESOURCE = "/lighting-engine/engine.wasm";
    private static final String REVISION_RESOURCE = "/lighting-engine/engine.revision";

    // the results of le_compile (see le_result_ptr)
    private static final int RESULT_SHOW = 0;
    private static final int RESULT_INDEX = 1;
    private static final int RESULT_MESSAGE = 2;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    // engine.wasm packed with Rocket Show, read and compiled once (see loadPacked)
    private static WasmModule packedModule;
    private static Function<Instance, Machine> packedMachine;

    private final Instance instance;
    private final Memory memory;

    // the functions of the boundary, looked up once
    final ExportFunction alloc;
    final ExportFunction free;
    private final ExportFunction features;
    private final ExportFunction formatVersion;
    private final ExportFunction behaviourVersion;
    private final ExportFunction compile;
    private final ExportFunction resultPointer;
    private final ExportFunction resultLength;
    private final ExportFunction resultFeatures;
    final ExportFunction engineNew;
    final ExportFunction engineFree;
    final ExportFunction playTimeline;
    final ExportFunction playPreview;
    final ExportFunction playLive;
    final ExportFunction stop;
    final ExportFunction selectPreset;
    final ExportFunction setStepPreview;
    final ExportFunction selectScene;
    final ExportFunction clearScenes;
    final ExportFunction setPosition;
    final ExportFunction hold;
    final ExportFunction go;
    final ExportFunction forgetHolds;
    final ExportFunction addHold;
    final ExportFunction setHolding;
    final ExportFunction setTempo;
    final ExportFunction tap;
    final ExportFunction beatOne;
    final ExportFunction setBeats;
    final ExportFunction liveScene;
    final ExportFunction liveFlash;
    final ExportFunction liveDimmer;
    final ExportFunction liveSpeed;
    final ExportFunction liveReset;
    final ExportFunction liveIdle;
    final ExportFunction setMasterDimmer;
    final ExportFunction setBlackout;
    final ExportFunction identify;
    final ExportFunction resetFixture;
    final ExportFunction lampOn;
    final ExportFunction setOverride;
    final ExportFunction clearOverrides;
    final ExportFunction audioFrame;
    final ExportFunction clearAudio;
    final ExportFunction render;
    final ExportFunction universesPointer;
    final ExportFunction universesLength;
    final ExportFunction valuesPointer;
    final ExportFunction flagsPointer;
    final ExportFunction valuesLength;

    /**
     * Load engine.wasm from its bytes, compiled to JVM bytecode or, with compiled false, interpreted (only to compare
     * the two).
     */
    public LightingEngine(byte[] wasm, boolean compiled) {
        this(Parser.parse(wasm), compiled);
    }

    private LightingEngine(WasmModule module, boolean compiled) {
        this(module, compiled ? compile(module) : null);
    }

    // a function too large for a JVM method is interpreted, instead of the module not loading
    private static Function<Instance, Machine> compile(WasmModule module) {
        return MachineFactoryCompiler.builder(module).withInterpreterFallback(InterpreterFallback.WARN).compile();
    }

    // an instance of the module, with the machine its code was compiled to (null: interpreted)
    private LightingEngine(WasmModule module, Function<Instance, Machine> machine) {
        // the memory of the module in a byte array: faster to read and write than a byte buffer
        Instance.Builder builder = Instance.builder(module).withMemoryFactory(ByteArrayMemory::new);

        if (machine != null) {
            builder.withMachineFactory(machine);
        }

        instance = builder.build();
        memory = instance.memory();

        alloc = function("le_alloc");
        free = function("le_free");
        features = function("le_features");
        formatVersion = function("le_format_version");
        behaviourVersion = function("le_behaviour_version");
        compile = function("le_compile");
        resultPointer = function("le_result_ptr");
        resultLength = function("le_result_len");
        resultFeatures = function("le_result_features");
        engineNew = function("le_engine_new");
        engineFree = function("le_engine_free");
        playTimeline = function("le_play_timeline");
        playPreview = function("le_play_preview");
        playLive = function("le_play_live");
        stop = function("le_stop");
        selectPreset = function("le_select_preset");
        setStepPreview = function("le_set_step_preview");
        selectScene = function("le_select_scene");
        clearScenes = function("le_clear_scenes");
        setPosition = function("le_set_position");
        hold = function("le_hold");
        go = function("le_go");
        forgetHolds = function("le_forget_holds");
        addHold = function("le_add_hold");
        setHolding = function("le_set_holding");
        setTempo = function("le_set_tempo");
        tap = function("le_tap");
        beatOne = function("le_beat_one");
        setBeats = function("le_set_beats");
        liveScene = function("le_live_scene");
        liveFlash = function("le_live_flash");
        liveDimmer = function("le_live_dimmer");
        liveSpeed = function("le_live_speed");
        liveReset = function("le_live_reset");
        liveIdle = function("le_live_idle");
        setMasterDimmer = function("le_set_master_dimmer");
        setBlackout = function("le_set_blackout");
        identify = function("le_identify");
        resetFixture = function("le_reset_fixture");
        lampOn = function("le_lamp_on");
        setOverride = function("le_set_override");
        clearOverrides = function("le_clear_overrides");
        audioFrame = function("le_audio_frame");
        clearAudio = function("le_clear_audio");
        render = function("le_render");
        universesPointer = function("le_universes_ptr");
        universesLength = function("le_universes_len");
        valuesPointer = function("le_values_ptr");
        flagsPointer = function("le_flags_ptr");
        valuesLength = function("le_values_len");
    }

    private ExportFunction function(String name) {
        ExportFunction function = instance.export(name);

        if (function == null) {
            throw new IllegalArgumentException("engine.wasm has no function " + name);
        }

        return function;
    }

    /**
     * The bytes of engine.wasm packed with Rocket Show, or null if this build has none.
     */
    public static byte[] readPacked() {
        try (InputStream stream = LightingEngine.class.getResourceAsStream(RESOURCE)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Whether this build of Rocket Show packs engine.wasm.
     */
    public static boolean isAvailable() {
        return LightingEngine.class.getResource(RESOURCE) != null;
    }

    /**
     * The revision of the lighting engine engine.wasm was built from, or null.
     */
    public static String packedRevision() {
        try (InputStream stream = LightingEngine.class.getResourceAsStream(REVISION_RESOURCE)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A new instance of engine.wasm packed with Rocket Show, compiled to bytecode: the module is read and compiled
     * once, each instance has a memory of its own (e.g. a new one after a fault of the engine). Throws if there is none.
     */
    public static LightingEngine loadPacked() {
        WasmModule module;
        Function<Instance, Machine> machine;

        synchronized (LightingEngine.class) {
            if (packedModule == null) {
                byte[] wasm = readPacked();

                if (wasm == null) {
                    throw new IllegalStateException("This build of Rocket Show has no engine.wasm (see testing/build-lighting-engine.sh)");
                }

                WasmModule parsed = Parser.parse(wasm);
                packedMachine = compile(parsed);
                packedModule = parsed;
            }

            module = packedModule;
            machine = packedMachine;
        }

        return new LightingEngine(module, machine);
    }

    // ---- what this build is ----

    // the feature bits the engine plays
    public synchronized int features() {
        return (int) features.apply()[0];
    }

    // the version of the compiled show format the engine reads: the major version times 65536 plus the minor one
    public synchronized int formatVersion() {
        return (int) formatVersion.apply()[0];
    }

    // counts up with every change of the engine which changes the values it comes to
    public synchronized int behaviourVersion() {
        return (int) behaviourVersion.apply()[0];
    }

    // ---- the memory of the module ----

    // memory for the bytes passed, written into it: free it with free(pointer, length)
    synchronized int put(byte[] bytes) {
        int pointer = (int) alloc.apply(bytes.length)[0];
        memory.write(pointer, bytes);
        return pointer;
    }

    synchronized void free(int pointer, int length) {
        free.apply(pointer, length);
    }

    Memory memory() {
        return memory;
    }

    // ---- the compiler ----

    /**
     * Compile the JSON of a project (of the current version) into a show for an engine with the passed feature bits.
     * With keepProject, the show carries the JSON along.
     */
    public synchronized CompiledShow compile(String projectJson, int features, boolean keepProject) {
        byte[] json = projectJson.getBytes(StandardCharsets.UTF_8);
        int pointer = put(json);
        int code;

        try {
            code = (int) compile.apply(pointer, json.length, features, keepProject ? 1 : 0)[0];
        } finally {
            free(pointer, json.length);
        }

        String message = new String(result(RESULT_MESSAGE), StandardCharsets.UTF_8);
        int resultFeatures = (int) this.resultFeatures.apply()[0];

        if (code != CompiledShow.SHOW) {
            return new CompiledShow(code, null, null, message, resultFeatures);
        }

        ShowIndex index = MAPPER.readValue(result(RESULT_INDEX), ShowIndex.class);
        return new CompiledShow(code, result(RESULT_SHOW), index, message, resultFeatures);
    }

    /**
     * Compile the JSON of a project for this engine.
     */
    public CompiledShow compile(String projectJson) {
        return compile(projectJson, features(), false);
    }

    private byte[] result(int kind) {
        int pointer = (int) resultPointer.apply(kind)[0];
        int length = (int) resultLength.apply(kind)[0];
        return length == 0 ? new byte[0] : memory.readBytes(pointer, length);
    }

    // ---- the engines ----

    /**
     * Make an engine for a compiled show, its clock starting at the passed moment (in microseconds of the clock the
     * host passes with every call). Throws for a show which doesn't pass its check.
     */
    public synchronized EngineShow play(byte[] show, ShowIndex index, long nowMicros) {
        int pointer = put(show);
        long handle;

        try {
            handle = engineNew.apply(pointer, show.length, nowMicros)[0];
        } finally {
            free(pointer, show.length);
        }

        if (handle == 0) {
            throw new IllegalArgumentException("The compiled show does not pass its check");
        }

        return new EngineShow(this, (int) handle, index);
    }

}
