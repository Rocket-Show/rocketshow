# The shared lighting engine in Rocket Show

Rocket Show plays the projects of the designer with an engine of its own, in Java (`lighting/designer`), which is kept
equal to the engine of the designer by hand. Both are to be replaced by one engine written once, in Rust, in the
repository `Rocket-Show/lighting-engine`. This document says how Rocket Show uses it. How the engine itself works (its
core, its compiler, the compiled show, the boundary of `engine.wasm`) is in that repository: `concept.md`,
`docs/format.md` and `README.md`.

Status: steps 1 to 3 built, 21 November 2026. `engine.wasm` plays in the JVM, compiled to bytecode by Endive, comes to
the very same bits as the native build of the engine, and plays beside the Java engine in shadow mode, which is off by
default. The Java engine still drives the lights. The frame time on SPARK's hardware is not measured yet (section 3),
and `engine.wasm` is not shipped yet (section 7).

## 1. Decisions

| Topic            | Decision                                                                                                                     |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| How it is loaded | **As WebAssembly inside the JVM**, the same `engine.wasm` the designer loads, run by Endive 1.2.0 and compiled to bytecode   |
| Fallback         | **A native library through JNA**, behind the same Java wrapper, if WebAssembly is too slow on SPARK's hardware               |
| The switch       | **Shadow mode for one release**, then the default; then the Java engine is deleted                                           |
| Old projects     | **Migrated in Java, as today**, then written as the designer writes them and handed over: the compiler reads version 11 only |
| New features     | **In the shared engine only**, from the moment the designer plays with it                                                    |

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
- **The shared engine** is in `lighting/engine` (section 4), next to the Java engine. Without `engine.wasm` in the
  build, it does nothing.

## 3. WebAssembly or JNA

Java can run the WebAssembly instead of a native library, and it should, provided the spike shows it fast enough on
SPARK's hardware.

The runtime is [Endive](https://foojay.io/today/endive-1-0-wasm/) 1.2.0 (released 30 October 2026), the continuation of
[Chicory](https://github.com/dylibso/chicory) under the Bytecode Alliance, Apache 2.0, from Maven Central (`run.endive`:
`runtime` and `compiler`, with ASM). It is pure Java: no native code, nothing per platform. Its compiler turns the
module into JVM bytecode when it is loaded (`MachineFactoryCompiler`), which the JVM then optimises like any Java code.
Its build-time compiler, which does the same in the build, is not used: it would need `engine.wasm` in the build, which
CI has not (section 7). A function too large for a JVM method would be left to Endive's interpreter, with a warning.

|                       | WebAssembly in the JVM                                                                                                     | Native library through JNA                                                                                                                               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What ships in the jar | One `engine.wasm`, the designer's file                                                                                     | One library per operating system and processor: Linux arm64 for SPARK and the Pi images, and every platform a developer or a Community Edition user runs |
| Equal to the designer | The same bytes, so equal by construction                                                                                   | The same source through another compiler back end: equal if the engine's rules hold, and tested per platform                                             |
| An engine fault       | A trap becomes a Java exception. Audio and video play on, and the engine restarts                                          | A crash in native code takes the whole JVM down, in the middle of a show                                                                                 |
| Speed                 | About ten times slower than native, measured on a Mac (below)                                                              | Native                                                                                                                                                   |
| Build                 | No cross-compilers; the jar stays pure Java                                                                                | A cross-compile matrix, and JNA with its own native part. Java's newer foreign-function API is final only from Java 22, and Rocket Show is on 21         |
| Debugging             | Harder: a trap gives a WebAssembly stack, not source lines                                                                 | The usual native tools                                                                                                                                   |
| Age                   | Young: 1.0 of Endive is five months old. Its compiler at run time works for `engine.wasm`, tested by the conformance cases | Decades                                                                                                                                                  |

**The budget.** Today's Java engine needs 0.5–1.1 ms a frame on a server core for the two test rigs, and SPARK's
processor is several times slower (_estimate_). The new engine does less work per frame than the Java engine, because
its compiler has resolved the tables: natively it takes 0.04 ms a frame for a rig of 60 moving heads on an Apple M6. The
runtime gives part of that back. The gate for the spike: a frame of the 60-fixture rig under 5 ms on SPARK's hardware,
compiled to bytecode and warmed up.

**What the spike measured**, on the JVM of a Mac (Apple silicon, OpenJDK 21.0.13, one thread), not on SPARK's hardware:
`LightingEngineBenchmark`, with the rigs of the engine's own benchmark, a frame being a render and the universes copied
out of the module, frames 25 ms apart, 4,000 of them after 200 to warm up.

| Rig                                       | Native (engine's benchmark) | JVM, compiled, cold (first 200 frames) | JVM, compiled, warm (median, 99th percentile) | JVM, interpreted, warm |
| ----------------------------------------- | --------------------------: | -------------------------------------: | --------------------------------------------: | ---------------------: |
| 30 moving heads (17 channels), two scenes |                    0.023 ms |                                 1.2 ms |                              0.18 ms, 0.53 ms |                 9.0 ms |
| 60 moving heads (17 channels), two scenes |                    0.036 ms |                                0.68 ms |                              0.35 ms, 0.68 ms |                  19 ms |
| 2,048 RGB pixels with a plasma            |                     0.43 ms |                                 4.2 ms |                                3.5 ms, 5.0 ms |                        |

The numbers vary by about 10 % from run to run, the Mac busy with other work. Loading `engine.wasm` and compiling it to
bytecode takes 0.3–0.4 s, compiling the project of 60 heads 10 ms and the one of 2,048 pixels 65 ms. The Java engine
takes 0.3–0.4 ms a frame for the 2,048 pixels on the same Mac (`DesignerFrameBenchmark`): for pixels, the new engine in
the JVM is about ten times slower than the Java engine.

**What the spike found.** The JVM compiles no method larger than 8,000 bytes of bytecode, and its compilers give up on
very large ones anyway. As the engine was built at revision `ed1768c`, LLVM had inlined a whole frame into one
WebAssembly function of 23.5 KB (a method of 29.9 KB), and the pixel patterns into one of 13.4 KB: they ran in the
interpreter of the JVM, 2.4 ms a frame for the 60 heads and 20 ms for the pixels. Raising the limits of the JVM's
compiler
(`-XX:-DontCompileHugeMethods -XX:+UnlockExperimentalVMOptions -XX:MaxNodeLimit=400000 -XX:NodeLimitFudgeFactor=8000`)
brought the 60 heads to 0.38 ms, with a long warm-up and a 99th percentile of 1.9 ms. Instead, the engine keeps the
stages of a frame and three groups of the pixel patterns functions of their own (merged into the engine's `main` on 21
November 2026, commit `5f84480`): no function of a frame is larger than 6 KB, nothing is computed differently (the
digests are the same) and the native frame times are unchanged. Rocket Show builds `engine.wasm` of the engine's `main`,
the same revision as the designer and the stick: `9801adf` since 21 November 2026, which keeps those functions apart in
the WebAssembly build only and works a curve out once for the fixtures it plays alike (no value changed).

**On SPARK's hardware the gate is met** (21 November 2026, a SPARK with a Raspberry Pi Compute Module 5, 4 cores, 2 GB,
OpenJDK 21.0.11, Rocket Show 3.3 running beside it, `engine.wasm` of `5f84480`):

| Rig                                       | Compile | First 200 frames (each) | Warm: median, 99th percentile | Interpreted |
| ----------------------------------------- | ------: | ----------------------: | ----------------------------: | ----------: |
| 30 moving heads, two scenes               |   66 ms |                 10.8 ms |               0.90 ms, 4.4 ms |       51 ms |
| 60 moving heads (17 channels), two scenes |   75 ms |                  2.3 ms |               1.77 ms, 2.2 ms |      102 ms |
| 2,048 RGB pixels with a plasma            |  366 ms |                 24.8 ms |              18.3 ms, 18.5 ms |             |

Loading `engine.wasm` (compiled to bytecode) takes 3.2 s there; all of it takes about five times as long as on a Mac.
The 60 heads take a third of the 5 ms gate. The pixels fit into a frame of 25 ms, but only just: a rig that large would
need more before the Java engine goes (step 4). The benchmark ran with the test classes and the libraries of the build
copied onto the device and a small main calling `LightingEngineBenchmark`, which the Maven command below does on a
checkout:

```shell
./mvnw -B test -Dtest=LightingEngineBenchmark -Dskip.npm -Dskip.installnodenpm
```

**Shadow mode on the same SPARK**, its jar laid over the installed one for the test (the device was put back
afterwards): the device's own project (a pixel tube of 16 pixels, a scene and a composition) over 3,767 frames, and the
30 moving heads with fans, a chase, a circle and steps over 2,180 frames, none of them differing by more than 1. Loading
a project keeps all four cores busy for about 30 seconds while the JVM compiles; then the whole of Rocket Show with both
engines takes about a quarter of one core for the 30 heads.

**Two things built in.** Shadow mode warms the engine up on load by rendering 300 frames of the project in an engine of
their own, since the first calls run before the JVM has optimised them. And the universes are read out of the module's
memory into a reused buffer, without a copy of them for each frame (the runtime still allocates the few small arrays of
each call).

**The fallback** is the same engine as a native library, called through JNA, behind the same Java wrapper. Choosing it
later changes one class (`LightingEngine`) and the build, not the engine.

## 4. What stays Java, and what goes

**Stays:** `DefaultLightingService` with OLA and the send timer, the scene and composition players, the MIDI mapping,
`TempoService`, the measuring of audio with GStreamer (`AudioAnalysisService`), the REST API, the project files and
their migrations.

**Goes:** the computing of `lighting/designer`. `DefaultDesignerService` shrinks from 4,400 lines to a wrapper that
passes inputs and time to the engine and copies its universes to OLA. `ArcTangent`, `CycleSine`, `PixelMath` and the
other classes which only compute go with it.

**What is built**, in `lighting/engine`:

- `LightingEngine`: `engine.wasm` in Endive, the `le_` functions as they are. The module is read and compiled once,
  every instance has a memory of its own. One call at a time.
- `EngineShow`: an engine playing a compiled show, its inputs in microseconds, the universes and the values of a frame.
- `DesignerEngine`: the wrapper of the table below. It takes a project migrated by `DefaultDesignerService` and written
  by `DesignerProjectJson` (the profiles as the designer writes them, which Rocket Show's model writes otherwise), keeps
  the index, turns uuids into numbers, real time into the engine's clock, and writes the universes to the
  `LightingUniverseState`s by the uuids they are mapped to.
- `ShadowEngine`: shadow mode (section 6).

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
  `conformance/DIFFERENCES.md` of the engine. In the universes, four of the five cases differ (the pixel effects
  `comet-2`, `image-1`, `meter-1` and `scope-1`); the leftover of 0 sends the same.
- **A project which was read differently before** is read the same: the engine's compiler mirrors how Jackson reads a
  project here (nulls, numbers written as text, the order of pixel groups).

## 6. Verification

1. **The conformance cases through `engine.wasm` in the JVM runtime**: the engine publishes a digest of the bits of
   every value of every frame of the `channelValues` cases (`conformance/core-digests.json`, copied to
   `src/test/resources/lighting-engine`), which the native build and `engine.wasm` in Node already come to. In the JVM,
   `LightingEngineConformanceTest` comes to the same bits for all 15 files, playing the JSON of the cases, and
   `DesignerProjectJsonTest` does too, handing them over as Rocket Show does: read into its model, migrated and written
   again. These tests are skipped where `engine.wasm` is missing, which includes CI (section 7).
   `DesignerConformanceTest` and the copy of the cases go once the Java engine does; until then the sync script keeps
   the copy.
2. **Shadow mode.** Before the switch, a release runs the Java engine on stage and the new engine beside it, and logs
   every frame which differs by more than 1. Real projects find what the cases don't. It is built (`ShadowEngine`) and
   off by default; see below.
3. **Speed.** The frame time of the two reference rigs on SPARK's hardware, measured in the spike and then in the
   hardware-in-the-loop rig (`testing/README.md`).
4. **A fault of the engine**: a trap during a show is caught, logged and the engine restarted, while audio and video
   play on. In shadow mode, a fault is logged once and the shadow pauses until the next project is loaded, with a new
   instance of the module; `DesignerShadowModeTest` and `LightingEngineTest` test it with an engine which fails to load
   and a trap caused on purpose.

**Shadow mode** is the setting `designerShadowEngine` (`true` in the settings file of the device, or through the API of
the settings); with each project loaded it follows the setting. It needs `engine.wasm` in the build: without it, a
warning is logged once and it stays off. While it is on, the send timer works out each frame with the Java engine as
before, and hands a copy of what played and of the universes it sent to a thread of its own, of the lowest priority,
which works out the same moment with the new engine and compares. A frame is dropped while the one before is still being
compared, so the timer never waits; nothing the new engine does reaches the show. The moments of the inputs are the ones
the Java engine used: the frame, the scenes picked by hand, the tempo and the taps of the beat clock.

What it logs, as warnings of `ShadowEngine`, at most one line a second, for example:

```text
Shadow engine: 3 channels differ by more than 1 (up to 30) in the project 'Tour', the composition 'Song' at 61250.0 ms
(real time 1795259407151): universe 4c1e… channel 200 (DMX address 201 Spot 3 Pan): Java 32768, engine 32700 (of
65535); …
```

with the number of frames which differed since the line before, and for each channel (up to 8): the universe, the
channel and its DMX address, the fixture and its channel, and both values, a channel with fine channels as one number.
When a project is replaced, a line says how many frames were compared and how many differed. Played over all 299
`channelValues` cases through the designer service (`DesignerShadowEngineTest`) and over a composition with a hold, the
scenes picked by hand with fades, flashes, dimmers, speed and a tempo tapped, and the preview of the designer with
faders and fixtures identified (`DesignerShadowModeTest`), only the four cases of section 5 differ.

What shadow mode doesn't compare exactly: a pixel effect following other audio buses than the other effects of the
project (the engine takes one stream of audio: it gets the buses all effects follow if they follow the same, else all
buses measured), and the beat clock in the middle of catching up a tap when a project is loaded (the new engine starts
on its beats, not on the catching up).

## 7. Plan

| Step           | What is built                                                                                                          | Gate                                                  | Status                                                                 |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ---------------------------------------------------------------------- |
| 1. Spike       | `engine.wasm` in Endive or Chicory on SPARK's hardware, compiled to bytecode and warmed up, playing the 60-fixture rig | A frame under 5 ms, or the native library through JNA | Endive 1.2.0: 0.35 ms on a Mac, 1.77 ms on SPARK (Compute Module 5)    |
| 2. Wrapper     | The wrapper of section 4 behind `DesignerService`, the conformance digests in CI                                       | The same bits as the native build                     | Built, the same bits. The tests skip in CI, which has no `engine.wasm` |
| 3. Shadow mode | One release with both engines, the differences logged                                                                  | A release without differences reported                | Built, off by default (`designerShadowEngine`)                         |
| 4. The switch  | The engine is the default; the Java engine, its tests and the copy of the conformance cases are deleted                |                                                       | Not started                                                            |

The designer switches first: the recorded values come from it, and it updates itself, so a fault is fixed for everyone
within the hour. Rocket Show on a stage is where a fault costs most, so it switches last and with shadow mode first.
Until step 4 is done there are three engines, not one: new engine features wait, or land in the shared engine only.

**`engine.wasm` is built, not kept in this repository.** The engine is open source since 21 November 2026
([Rocket-Show/lighting-engine](https://github.com/Rocket-Show/lighting-engine), GPL-3.0-or-later, like Rocket Show).
`testing/build-lighting-engine.sh` builds the file at the revision Rocket Show plays with (from a checkout of the engine
next to this repository, or else cloned from GitHub; it needs Rust) into `src/main/resources/lighting-engine`, which is
ignored by git, and the build packs it into the jar from there: run it before the jar for a release is built. Without
it, Rocket Show builds and plays as before. CI builds it before the unit tests, so the tests of the engine run there.

## 8. Risks

| Risk                                                   | What it would change                             | How it is settled                                                       |
| ------------------------------------------------------ | ------------------------------------------------ | ----------------------------------------------------------------------- |
| WebAssembly in the JVM is too slow on SPARK's hardware | Rocket Show takes the native library through JNA | The spike on SPARK's hardware (section 3)                               |
| The runtime's build-time compiler is not ready         | The interpreter if it is fast enough, or JNA     | Settled: the compiler at load time is used, the interpreter is too slow |
| A function of the engine grows too large for the JVM   | It runs in the interpreter, many times slower    | The benchmark of each new revision of the engine (section 3)            |
| Three engines during the migration drift apart         | A longer migration                               | The feature freeze                                                      |
| Single precision is visibly worse somewhere            | A fix in the shared engine                       | Shadow mode on real rigs                                                |

Sources: the lighting engine (`Rocket-Show/lighting-engine`: `concept.md`, `docs/format.md`, `README.md`,
`conformance/DIFFERENCES.md`), [Chicory](https://github.com/dylibso/chicory),
[Endive 1.0](https://foojay.io/today/endive-1-0-wasm/) (29 June 2026). All read on 11 November 2026. Endive 1.2.0 on
Maven Central, 21 November 2026.
