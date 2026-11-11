# The shared lighting engine in Rocket Show

Rocket Show plays the projects of the designer with an engine of its own, in Java (`lighting/designer`), which is kept
equal to the engine of the designer by hand. Both are to be replaced by one engine written once, in Rust, in the
repository `Rocket-Show/lighting-engine`. This document says how Rocket Show uses it. How the engine itself works (its
core, its compiler, the compiled show, the boundary of `engine.wasm`) is in that repository: `concept.md`,
`docs/format.md` and `README.md`.

Status: plan, 11 November 2026. The engine exists and passes the conformance cases of this repository within ±1 (see
"What changes on stage"). Nothing of it is in Rocket Show yet.

## 1. Decisions

| Topic            | Decision                                                                                                                    |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------- |
| How it is loaded | **As WebAssembly inside the JVM**, the same `engine.wasm` the designer loads, with whichever Java WebAssembly runtime works |
| Fallback         | **A native library through JNA**, behind the same Java wrapper, if WebAssembly is too slow on SPARK's hardware              |
| The switch       | **Shadow mode for one release**, then the default; then the Java engine is deleted                                          |
| Old projects     | **Migrated in Java, as today**, before they are handed over: the engine's compiler reads the current project version only   |
| New features     | **In the shared engine only**, from the moment the designer plays with it                                                   |

## 2. Today

- **The Java engine** is `lighting/designer`: about 14,500 lines, `DefaultDesignerService` alone 4,400. It computes in
  `double` throughout (about 1,000 declarations) and counts time in milliseconds as a `double`.
- **The conformance cases** in `src/test/resources/designer-conformance` are a copy of the ones of the designer
  (`testing/sync-designer-conformance.sh`); `DesignerConformanceTest` compares them exactly, and CI checks that the copy
  is the designer's.
- **The discipline for equal numbers**: `ArcTangent`, `CycleSine` and `PixelMath` exist in both languages, because
  `Math.atan2` and `Math.sin` round differently in Java and in the browser.
- **The measured frame times**: after the chase fix, the Java engine takes 0.5 ms a frame for 30 fixtures and 600
  channels, and 1.1 ms for 60 fixtures and 1,200 channels, on one core of a 2.3 GHz Xeon. A frame has 25 ms at 40 Hz.

## 3. WebAssembly or JNA

Java can run the WebAssembly instead of a native library, and it should, provided the spike shows it fast enough on
SPARK's hardware.

The first runtime to try is [Chicory](https://github.com/dylibso/chicory), which describes itself as running WebAssembly
"with zero native dependencies or JNI". Since June 2026 it continues as
[Endive](https://foojay.io/today/endive-1-0-wasm/) under the Bytecode Alliance, with the same code and API and the Maven
group `run.endive`. It has an interpreter and a build-time compiler that turns a module into JVM bytecode, which the JVM
then optimises like any Java code. Which runtime it is in the end is not a design question: whichever works.

|                       | WebAssembly in the JVM                                                                                                    | Native library through JNA                                                                                                                               |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What ships in the jar | One `engine.wasm`, the designer's file                                                                                    | One library per operating system and processor: Linux arm64 for SPARK and the Pi images, and every platform a developer or a Community Edition user runs |
| Equal to the designer | The same bytes, so equal by construction                                                                                  | The same source through another compiler back end: equal if the engine's rules hold, and tested per platform                                             |
| An engine fault       | A trap becomes a Java exception. Audio and video play on, and the engine restarts                                         | A crash in native code takes the whole JVM down, in the middle of a show                                                                                 |
| Speed                 | Slower than native; how much is the open number. Chicory states that being the fastest runtime is not its goal            | Native                                                                                                                                                   |
| Build                 | No cross-compilers; the jar stays pure Java                                                                               | A cross-compile matrix, and JNA with its own native part. Java's newer foreign-function API is final only from Java 22, and Rocket Show is on 21         |
| Debugging             | Harder: a trap gives a WebAssembly stack, not source lines                                                                | The usual native tools                                                                                                                                   |
| Age                   | Young: 1.0 of Endive is five months old, and whether its build-time compiler is production-ready is to check in the spike | Decades                                                                                                                                                  |

**The budget.** Today's Java engine needs 0.5–1.1 ms a frame on a server core for the two test rigs, and SPARK's
processor is several times slower (_estimate_). The new engine does less work per frame than the Java engine, because
its compiler has resolved the tables: natively it takes 0.04 ms a frame for a rig of 60 moving heads on an Apple M6. The
runtime gives part of that back. The gate for the spike: a frame of the 60-fixture rig under 5 ms on SPARK's hardware,
compiled to bytecode and warmed up.

**Two things to build in.** Warm the engine up on load by rendering a few hundred frames, since the first calls run
before the JVM has optimised them. And read the universes out of the module's memory into a reused buffer, so a frame
allocates nothing.

**The fallback** is the same engine as a native library, called through JNA, behind the same Java wrapper. Choosing it
later changes one class and the build, not the engine.

## 4. What stays Java, and what goes

**Stays:** `DefaultLightingService` with OLA and the send timer, the scene and composition players, the MIDI mapping,
`TempoService`, the measuring of audio with GStreamer (`AudioAnalysisService`), the REST API, the project files and
their migrations.

**Goes:** the computing of `lighting/designer`. `DefaultDesignerService` shrinks from 4,400 lines to a wrapper that
passes inputs and time to the engine and copies its universes to OLA. `ArcTangent`, `CycleSine`, `PixelMath` and the
other classes which only compute go with it.

The wrapper, call by call (the functions of `engine.wasm` start with `le_`):

| Rocket Show today                                                          | With the engine                                                                                                                          |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `load(compositionPlayer, project, pipeline)`                               | Migrate the project, hand its JSON to `le_compile`, keep the index (which uuid has which number) and make an engine for the show         |
| `calculateUniverse` on the send timer                                      | `le_render` with the time in microseconds, then copy the universes from the module's memory to the `LightingUniverseState`s by the index |
| The position of the `CompositionPlayer`                                    | `le_set_position` when it starts, pauses, seeks or drifts; the engine counts on from there                                               |
| `hold`, `endHold`, `forgetHolds`                                           | `le_hold`, `le_go`, `le_forget_holds`                                                                                                    |
| `setOutsideBeatsPerMinute`, the taps and beat one of `TempoService`        | `le_set_tempo`, `le_tap`, `le_beat_one`; the beat clock is in the engine                                                                 |
| `playManualScenes`, `releaseManualScenes`, the live dimmers and speed      | `le_play_live`, `le_live_scene`, `le_live_flash`, `le_live_dimmer`, `le_live_speed`; `le_live_idle` says when they have faded out        |
| `setSelectedSceneUuids`, `setSelectedPresetUuid`, the step preview         | `le_play_preview`, `le_select_scene`, `le_select_preset`, `le_set_step_preview`, with the numbers of the index                           |
| `setIdentifyFixtureUuids`, `setMaintenanceFixtureUuids`, `setDmxOverrides` | `le_identify`, `le_reset_fixture`, `le_lamp_on`, `le_set_override`                                                                       |
| `AudioAnalysisService.frame(buses, index)`                                 | `le_audio_frame` for each frame of 20 ms: the buses an effect follows are still added in Java (today the engine takes one stream)        |
| The master dimmer of the project and of the Play screen                    | The one of the project is in the show; `le_set_master_dimmer` sets the one of the Play screen                                            |

Still to decide: `getCompositionEndMillis` (where the lights of a composition have gone out) is asked of a project
without playing it. It stays in Java, or the engine's compiler returns it with the index.

## 5. What changes on stage

The engine computes in single precision and on whole nanoseconds, where the Java engine computes in doubles.

- **Values differ by at most 1** (of 255, or of 65,535 for a channel with a fine channel): all 299 `channelValues` cases
  of this repository, 3,811 frames and 209,059 values, are within that.
- **136 of those values differ more**, in five cases, each exactly on an edge where the Java engine itself decides by
  the rounding of a double: a place in a palette on the border of two stepped stops, a pixel on the border of two
  columns of an image, and a leftover of 0 which doubles bring a hair below 0. They are listed in
  `conformance/DIFFERENCES.md` of the engine.
- **A project which was read differently before** is read the same: the engine's compiler mirrors how Jackson reads a
  project here (nulls, numbers written as text, the order of pixel groups).

## 6. Verification

1. **The conformance cases through `engine.wasm` in the JVM runtime**, in this repository's CI: the engine publishes a
   digest of the bits of every value of every frame of the `channelValues` cases (`conformance/core-digests.json`),
   which the native build and `engine.wasm` in Node already come to. The JVM runtime has to come to the same bits.
   `DesignerConformanceTest` and the copy of the cases go once the Java engine does; until then the sync script keeps
   the copy.
2. **Shadow mode.** Before the switch, a release runs the Java engine on stage and the new engine beside it, and logs
   every frame which differs by more than 1. Real projects find what the cases don't.
3. **Speed.** The frame time of the two reference rigs on SPARK's hardware, measured in the spike and then in the
   hardware-in-the-loop rig (`testing/README.md`).
4. **A fault of the engine**: a trap during a show is caught, logged and the engine restarted, while audio and video
   play on. Tested with a show damaged on purpose.

## 7. Plan

| Step           | What is built                                                                                                          | Gate                                                  |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| 1. Spike       | `engine.wasm` in Endive or Chicory on SPARK's hardware, compiled to bytecode and warmed up, playing the 60-fixture rig | A frame under 5 ms, or the native library through JNA |
| 2. Wrapper     | The wrapper of section 4 behind `DesignerService`, the conformance digests in CI                                       | The same bits as the native build                     |
| 3. Shadow mode | One release with both engines, the differences logged                                                                  | A release without differences reported                |
| 4. The switch  | The engine is the default; the Java engine, its tests and the copy of the conformance cases are deleted                |                                                       |

The designer switches first: the recorded values come from it, and it updates itself, so a fault is fixed for everyone
within the hour. Rocket Show on a stage is where a fault costs most, so it switches last and with shadow mode first.
Until step 4 is done there are three engines, not one: new engine features wait, or land in the shared engine only.

## 8. Risks

| Risk                                                   | What it would change                             | How it is settled        |
| ------------------------------------------------------ | ------------------------------------------------ | ------------------------ |
| WebAssembly in the JVM is too slow on SPARK's hardware | Rocket Show takes the native library through JNA | The spike                |
| The runtime's build-time compiler is not ready         | The interpreter if it is fast enough, or JNA     | The spike                |
| Three engines during the migration drift apart         | A longer migration                               | The feature freeze       |
| Single precision is visibly worse somewhere            | A fix in the shared engine                       | Shadow mode on real rigs |

Sources: the lighting engine (`Rocket-Show/lighting-engine`: `concept.md`, `docs/format.md`, `README.md`,
`conformance/DIFFERENCES.md`), [Chicory](https://github.com/dylibso/chicory),
[Endive 1.0](https://foojay.io/today/endive-1-0-wasm/) (29 June 2026). All read on 11 November 2026.
