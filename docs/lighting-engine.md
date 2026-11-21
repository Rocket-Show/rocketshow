# The shared lighting engine in Rocket Show

Rocket Show plays the projects of the designer with the lighting engine shared with the designer, written once, in Rust,
in the repository `Rocket-Show/lighting-engine`, and loaded into the JVM as WebAssembly. Until 21 November 2026 it
played them with an engine of its own, in Java (`lighting/designer`), kept equal to the engine of the designer by hand.
This document says how Rocket Show uses the shared engine. How the engine itself works (its core, its compiler, the
compiled show, the boundary of `engine.wasm`) is in that repository: `concept.md`, `docs/format.md` and `README.md`.

Status: step 4 done, 21 November 2026. `engine.wasm`, compiled to bytecode by Endive, works out every universe Rocket
Show sends: the compositions, the live preview of the designer and the scenes picked by hand on the Play screen (section
4). The Java engine and shadow mode are deleted. Every build packs `engine.wasm`: the Maven build fails without it
(section 7). On SPARK's hardware, a frame of 60 moving heads takes 2.2 ms, one of 2,048 pixels 17 ms (section 3).

## 1. Decisions

| Topic            | Decision                                                                                                                     |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| How it is loaded | **As WebAssembly inside the JVM**, the same `engine.wasm` the designer loads, run by Endive 1.2.0 and compiled to bytecode   |
| Fallback         | **A native library through JNA**, behind the same Java wrapper, if WebAssembly is too slow on SPARK's hardware               |
| The switch       | **Shadow mode, then the Java engine deleted**: decided on 21 November 2026, without a release in shadow mode (section 7)     |
| Old projects     | **Migrated in Java, as today**, then written as the designer writes them and handed over: the compiler reads version 11 only |
| New features     | **In the shared engine only**, from the moment the designer plays with it                                                    |

## 2. Before the switch

- **The Java engine** was `lighting/designer`: about 15,500 lines, `DefaultDesignerService` alone 4,700. It computed in
  `double` throughout and counted time in milliseconds as a `double`. It took 0.5 ms a frame for 30 fixtures and 600
  channels, and 1.1 ms for 60 fixtures and 1,200 channels, on one core of a 2.3 GHz Xeon.
- **The conformance cases** of the designer were played by it and compared exactly (`DesignerConformanceTest`), with
  `ArcTangent`, `CycleSine` and `PixelMath` in both languages, because `Math.atan2` and `Math.sin` round differently in
  Java and in the browser.
- **Shadow mode** played the shared engine beside it (section 6).

## 3. WebAssembly or JNA

Java can run the WebAssembly instead of a native library, and it should, provided the spike shows it fast enough on
SPARK's hardware.

The runtime is [Endive](https://foojay.io/today/endive-1-0-wasm/) 1.2.0 (released 30 October 2026), the continuation of
[Chicory](https://github.com/dylibso/chicory) under the Bytecode Alliance, Apache 2.0, from Maven Central (`run.endive`:
`runtime` and `compiler`, with ASM). It is pure Java: no native code, nothing per platform. Its compiler turns the
module into JVM bytecode when it is loaded (`MachineFactoryCompiler`), which the JVM then optimises like any Java code.
Its build-time compiler, which does the same in the build, is not used (yet): the module is compiled once when Rocket
Show starts (section 4). A function too large for a JVM method would be left to Endive's interpreter, with a warning.

|                       | WebAssembly in the JVM                                                                                                     | Native library through JNA                                                                                                                               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What ships in the jar | One `engine.wasm`, the designer's file                                                                                     | One library per operating system and processor: Linux arm64 for SPARK and the Pi images, and every platform a developer or a Community Edition user runs |
| Equal to the designer | The same bytes, so equal by construction                                                                                   | The same source through another compiler back end: equal if the engine's rules hold, and tested per platform                                             |
| An engine fault       | A trap becomes a Java exception. Audio and video play on, and the engine restarts                                          | A crash in native code takes the whole JVM down, in the middle of a show                                                                                 |
| Speed                 | About ten times slower than native, measured on a Mac (below)                                                              | Native                                                                                                                                                   |
| Build                 | No cross-compilers; the jar stays pure Java                                                                                | A cross-compile matrix, and JNA with its own native part. Java's newer foreign-function API is final only from Java 22, and Rocket Show is on 21         |
| Debugging             | Harder: a trap gives a WebAssembly stack, not source lines                                                                 | The usual native tools                                                                                                                                   |
| Age                   | Young: 1.0 of Endive is five months old. Its compiler at run time works for `engine.wasm`, tested by the conformance cases | Decades                                                                                                                                                  |

**The budget.** The Java engine needed 0.5–1.1 ms a frame on a server core for the two test rigs, and SPARK's processor
is several times slower (_estimate_). The new engine does less work per frame than the Java engine, because its compiler
has resolved the tables: natively it takes 0.04 ms a frame for a rig of 60 moving heads on an Apple M6. The runtime
gives part of that back. The gate for the spike: a frame of the 60-fixture rig under 5 ms on SPARK's hardware, compiled
to bytecode and warmed up.

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
took 0.3–0.4 ms a frame for the 2,048 pixels on the same Mac: for pixels, the new engine in the JVM is about ten times
slower than the Java engine was.

**What the spike found.** The JVM compiles no method larger than 8,000 bytes of bytecode, and its compilers give up on
very large ones anyway. As the engine was built at revision `ed1768c`, LLVM had inlined a whole frame into one
WebAssembly function of 23.5 KB (a method of 29.9 KB), and the pixel patterns into one of 13.4 KB: they ran in the
interpreter of the JVM, 2.4 ms a frame for the 60 heads and 20 ms for the pixels. Raising the limits of the JVM's
compiler
(`-XX:-DontCompileHugeMethods -XX:+UnlockExperimentalVMOptions -XX:MaxNodeLimit=400000 -XX:NodeLimitFudgeFactor=8000`)
brought the 60 heads to 0.38 ms, with a long warm-up and a 99th percentile of 1.9 ms. Instead, the engine keeps the
stages of a frame and three groups of the pixel patterns functions of their own (merged into the engine's `main` on 21
November 2026, commit `5f84480`): no function of a frame is larger than 6 KB, nothing is computed differently (the
digests are the same) and the native frame times are unchanged. Rocket Show builds `engine.wasm` from that revision, the
same one as the designer.

**On SPARK's hardware the gate is met** (21 November 2026, a SPARK with a Raspberry Pi Compute Module 5, 4 cores, 2 GB,
OpenJDK 21.0.11, Rocket Show 3.3 running beside it, `engine.wasm` of `5f84480`):

| Rig                                       | Compile | First 200 frames (each) | Warm: median, 99th percentile | Interpreted |
| ----------------------------------------- | ------: | ----------------------: | ----------------------------: | ----------: |
| 30 moving heads, two scenes               |   66 ms |                 10.8 ms |               0.90 ms, 4.4 ms |       51 ms |
| 60 moving heads (17 channels), two scenes |   75 ms |                  2.3 ms |               1.77 ms, 2.2 ms |      102 ms |
| 2,048 RGB pixels with a plasma            |  366 ms |                 24.8 ms |              18.3 ms, 18.5 ms |             |

Loading `engine.wasm` (compiled to bytecode) takes 3.2 s there; all of it takes about five times as long as on a Mac.
The 60 heads take a third of the 5 ms gate. The pixels fit into a frame of 25 ms, but only just; since the timer doesn't
wait for the engine (section 4), a frame too slow is dropped rather than late. The benchmark ran with the test classes
and the libraries of the build copied onto the device and a small main calling `LightingEngineBenchmark`, which the
Maven command below does on a checkout:

```shell
./mvnw -B test -Dtest=LightingEngineBenchmark -Dskip.npm -Dskip.installnodenpm
```

**Shadow mode on the same SPARK**, its jar laid over the installed one for the test (the device was put back
afterwards): the device's own project (a pixel tube of 16 pixels, a scene and a composition) over 3,767 frames, and the
30 moving heads with fans, a chase, a circle and steps over 2,180 frames, none of them differing by more than 1. Loading
a project keeps all four cores busy for about 30 seconds while the JVM compiles; then the whole of Rocket Show with both
engines takes about a quarter of one core for the 30 heads.

**The engine driving the lights on the same SPARK** (21 November 2026, the jar of step 4 laid over the installed one,
the device put back afterwards, logging at INFO): each project played in the live preview of the designer at 40 Hz, its
universe sent to OLA (read back from OLA's universe 1 while it played). A frame is all the engine does for it on its
thread (the inputs handed over, the render, the universes copied out), measured over a whole minute after the first; the
processor time is the one of the whole Java process of Rocket Show, `top` every 10 s over a minute.

| Project                                       | Compile | A frame: average, slowest | Frames dropped in the minute | Rocket Show's process |
| --------------------------------------------- | ------: | ------------------------: | ---------------------------: | --------------------: |
| The device's own (a pixel tube of 16 pixels)  |   38 ms |           0.26 ms, 12 ms¹ |                            0 |     3–5 % of one core |
| 30 moving heads, two scenes                   |  129 ms |           1.13 ms, 6.3 ms |                            0 |    6–14 % of one core |
| 60 moving heads (two universes), two scenes   |  129 ms |           2.19 ms, 3.2 ms |                            0 |    9–11 % of one core |
| 2,048 RGB pixels (16 universes) with a plasma |  880 ms |            17.3 ms, 40 ms |                            1 |   69–73 % of one core |

¹ the first minute after the start. In the minute of a project loaded, its first frames are slower and a few are dropped
while it is compiled (7 for the 30 heads, 46 for the 2,048 pixels): the timer sends the frame before again. The module
is loaded and warmed up when Rocket Show starts (3.5 s there, beside the start of the app, which took as long as
before); a project played again isn't compiled again. Also played there: the timeline of a composition in the preview, a
composition with nothing but lights through the API with a hold point (the lights ran on while it held, and on from GO),
and the scenes picked by hand with a flash, the speed master, taps, the master dimmer, the blackout and their release.
The device's compositions with audio could not play, its sound card wasn't connected.

**Built in.** The universes are read out of the module's memory without a copy for each value (the runtime still
allocates the few small arrays of each call), and copied once for the timer.

**The fallback** is the same engine as a native library, called through JNA, behind the same Java wrapper. Choosing it
later changes one class (`LightingEngine`) and the build, not the engine.

## 4. What drives the lights

**The engine on a thread of its own.** `DefaultDesignerService` keeps what plays: the project loaded, the composition
and its position, the holds it went through, what the designer previews, the scenes picked by hand, the tempo, the
fixtures identified and the faders of the designer. `EngineRenderer` plays it with the engine, on a thread of its own
(`lighting-engine`), one task after the other: the project when it is loaded, the events with their moments (the scenes
picked, flashed and let go, their dimmers and speed, the tempo, the taps of the beat clock) and the frames.

**The send timer never waits for the engine.** Each period (25 ms at 40 Hz) the timer sends the frame the engine worked
out for that moment, and hands it what plays now for the frame of the next period, which the engine works out while the
timer waits. The position of the timeline is taken now and runs on to the moment of the frame, so the lights stay with
the audio as they did. A frame the engine hasn't finished in time is not waited for: the timer sends what it sent last
again, and the frame asked for meanwhile is dropped. Until the first frame of a project loaded is worked out, nothing is
sent: what was sent last stays on the wire.

**Loading a project.** The project is migrated (`ProjectMigration`, Java as before: the compiler reads the current
version only) and written as the designer writes it (`DesignerProjectJson`), without what the designer previews, which
the engine is handed as inputs of its own. The engine thread compiles it, unless it compiled the same JSON last (the
projects of the compositions of a set, the designer changing its selection, the scenes picked by hand), and makes an
engine for the show. The beat clock of the engine starts where the beat clock of Rocket Show (`BeatClock`, which the
apps and `TempoService` use) is, and is told the same taps.

**A fault of the engine** (a trap) is caught and logged, and the engine loaded anew with the project playing, once. If
it fails again, the engine pauses until the next project is loaded, while audio and video play on; the lights keep what
was sent last. A project the compiler can't make a show of is logged the same way.

**Warming up.** When Rocket Show starts, a thread of the lowest priority loads the module and renders 300 frames of each
of the first three projects of the unit, in an engine of their own: the JVM compiles the engine before a show, not
during its first song.

The state of Rocket Show and the inputs of the engine (the functions of `engine.wasm` start with `le_`):

| Rocket Show                                                                                                    | The engine                                                                                                                                                                                                                                             |
| -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `load(compositionPlayer, project, pipeline)`                                                                   | Migrate the project, hand its JSON to `le_compile` (unless compiled last), keep the index (which uuid has which number), `le_engine_new`                                                                                                               |
| The send timer                                                                                                 | `le_render` for the moment of the next send, then the universes copied out of the module's memory and written to the `LightingUniverseState`s by the uuid they are mapped to (all 512 channels)                                                        |
| What plays: a composition playing or holding, else the scenes picked by hand, else the preview of the designer | `le_play_timeline` (as the show, or as the preview leaving the hidden tracks out; the composition played in between follows a tempo from outside), `le_play_live`, `le_play_preview` with `le_select_preset`, `le_set_step_preview`, `le_select_scene` |
| The position of the `CompositionPlayer` (or the clock of the preview)                                          | `le_set_position` with each frame, running unless it holds or the player doesn't play                                                                                                                                                                  |
| `hold`, `endHold`, `forgetHolds`, the holds of the preview                                                     | The holds with the time held, `le_add_hold` and `le_set_holding`, with each frame while there are any                                                                                                                                                  |
| `setOutsideBeatsPerMinute`, the taps and beat one of `TempoService`                                            | `le_set_tempo`, `le_tap`, `le_beat_one`, `le_set_beats`, each with its moment                                                                                                                                                                          |
| `playManualScenes`, `releaseManualScenes`, the live dimmers and speed                                          | `le_live_scene`, `le_live_flash`, `le_live_dimmer`, `le_live_speed`; `le_live_idle` says when the scenes let go have faded out                                                                                                                         |
| `setIdentifyFixtureUuids`, `setMaintenanceFixtureUuids`, `setDmxOverrides`                                     | `le_identify`, `le_reset_fixture`, `le_lamp_on`, `le_set_override`                                                                                                                                                                                     |
| `AudioAnalysisService.frame(buses, index)`                                                                     | `le_audio_frame` for each frame of 20 ms up to the position of the frame: the buses the effects follow, added together (below)                                                                                                                         |
| The master dimmer of the project and of the Play screen (and its blackout)                                     | The one of the project is in the show; `le_set_master_dimmer` sets the one of the Play screen                                                                                                                                                          |

`getCompositionEndMillis` (where the lights of a composition have gone out, which a composition plays at least) is asked
of a project without playing it: it stays in Java, as project metadata.

**The audio buses.** An effect of the designer names the audio buses it follows (all of them, if it names none), the
engine takes one stream of audio. Rocket Show measures the buses the pixel effects of the project follow, and hands the
engine these buses added together (their power added up, the loudest peak), as `AudioAnalysisService` adds the buses of
an effect. A project whose effects all follow the same buses (the usual case: one effect, or the effects following the
music) plays as before. Where two effects follow different buses (one the drums, one the vocals), each follows them all
together now. Following each its own buses would need the engine to take several streams; until it does, that is a known
difference.

**What went** (about 10,100 lines of `src/main`, 4,700 of tests): the computing of `lighting/designer`, which went from
15,500 lines to 7,300: `DefaultDesignerService` from 4,700 lines to 1,100 (the projects, what plays and the send timer),
the effects, the pixel patterns with their palettes, audio, pictures and stage, the curves, fans, steps and their
orders, the selection patterns, the capability positions, the colors of emitters and filters, programs, identify and
maintenance, the transition curves, the cached fixtures, the resolution of pixel groups, the live scenes; shadow mode
(`ShadowEngine`, its setting `designerShadowEngine`, never released); the tests of all these, the Java engine's own
conformance run (`DesignerConformanceTest`) and the conformance cases only it played (15 of 32 files); the weekly
mutation tests, which targeted the Java engine.

**What stays Java:** `DefaultLightingService` with OLA, the scene and composition players, the MIDI mapping,
`TempoService` and `BeatClock`, the measuring of audio with GStreamer (`AudioAnalysisService`), the REST API, the
project files with their model (every field the designer writes, which is what the engine is handed) and their
migrations, the end of a composition, the fixture library.

## 5. What changes on stage

The engine computes in single precision and on whole nanoseconds, where the Java engine computed in doubles.

- **Values differ by at most 1** (of 255, or of 65,535 for a channel with a fine channel): all 299 `channelValues` cases
  of this repository, 3,811 frames and 209,059 values, are within that.
- **136 of those values differ more**, in five cases, each exactly on an edge where the Java engine itself decides by
  the rounding of a double: a place in a palette on the border of two stepped stops, a pixel on the border of two
  columns of an image, and a leftover of 0 which doubles bring a hair below 0. They are listed in
  `conformance/DIFFERENCES.md` of the engine. In the universes, four of the five cases differ (the pixel effects
  `comet-2`, `image-1`, `meter-1` and `scope-1`); the leftover of 0 sends the same.
- **A project which was read differently before** is read the same: the engine's compiler mirrors how Jackson reads a
  project here (nulls, numbers written as text, the order of pixel groups).
- **The audio buses**: effects of one project following different buses follow them all together (section 4).
- **A frame is worked out a period ahead**, from what plays when it is asked for: an input (a scene picked, a fader)
  reaches the lights at the second send after it, one period later than before, at its own moment (a fade starts when
  the scene was picked). The timeline runs on for that period: at a hold point, one frame may show it up to a period
  past the point, before the hold reaches the engine.
- **A slow frame is dropped**, not waited for: the timer sends the one before again. Before, the timer waited for the
  Java engine.
- **The universes are sent whole**: all 512 channels of a universe of the project, the ones without a fixture at 0 (the
  same on the wire: the universes of Rocket Show are mixed by their highest value).

## 6. Verification

1. **The conformance cases through `engine.wasm` in the JVM runtime**: the engine publishes a digest of the bits of
   every value of every frame of the `channelValues` cases (`conformance/core-digests.json`, copied to
   `src/test/resources/lighting-engine`), which the native build and `engine.wasm` in Node come to. In the JVM,
   `LightingEngineConformanceTest` comes to the same bits for all 15 files, playing the JSON of the cases, and
   `DesignerProjectJsonTest` does too, handing them over as Rocket Show does: read into its model, migrated and written
   again. The cases are a copy of the ones of the designer (`src/test/resources/designer-conformance`, the kinds
   `channelValues` and `migration`, `testing/sync-designer-conformance.sh`), which CI checks.
2. **The migration of old projects**: `DesignerMigrationConformanceTest` comes to exactly the projects the designer
   migrated the `migration` cases to.
3. **Rocket Show playing through the engine**: the tests of the designer service play their projects through the engine
   and check what is sent (`DesignerDmxTest`, `DesignerTracksTest`, `DesignerSendTimerTest`, `DesignerHoldsTest`,
   `FixtureLibraryTest`, `ScenePlayerServiceTest` and the API tests), and `DesignerLightingEngineTest` the send timer
   going on while the engine is busy, a fault of the engine (loaded anew, then paused) and the audio buses.
4. **Shadow mode** (step 3, deleted with the Java engine): before the switch, the shared engine played beside the Java
   engine over all 299 `channelValues` cases through the designer service, a composition with a hold, the scenes picked
   by hand with fades, flashes, dimmers, speed and a tempo tapped, and the preview of the designer with faders and
   fixtures identified, and live on a SPARK (section 3): no value differed by more than 1, but the four cases of
   section 5.
5. **Speed**: the frame times on SPARK's hardware (section 3), and `LightingEngineBenchmark` for a new revision of the
   engine (a function too large for the JVM runs in its interpreter, many times slower).

## 7. Plan

| Step           | What is built                                                                                                          | Gate                                                  | Status                                                              |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------------- |
| 1. Spike       | `engine.wasm` in Endive or Chicory on SPARK's hardware, compiled to bytecode and warmed up, playing the 60-fixture rig | A frame under 5 ms, or the native library through JNA | Endive 1.2.0: 0.35 ms on a Mac, 1.77 ms on SPARK (Compute Module 5) |
| 2. Wrapper     | The wrapper of section 4 behind `DesignerService`, the conformance digests in CI                                       | The same bits as the native build                     | Built, the same bits, in CI                                         |
| 3. Shadow mode | Both engines, the differences logged                                                                                   | No differences reported                               | None but the edges of section 5, on the cases and live on a SPARK   |
| 4. The switch  | The engine drives the lights; the Java engine, its tests and the cases only it played are deleted                      | The frame times on SPARK's hardware                   | Done, 21 November 2026 (sections 3 and 4)                           |

The designer was to switch first: the recorded values come from it, and it updates itself, so a fault is fixed for
everyone within the hour. Rocket Show on a stage is where a fault costs most, so it was to switch last, after shadow
mode. The owner decided on 21 November 2026 that the Java engine could be retired now: shadow mode had found no
difference but the edges of section 5. The designer (1.83.0) has the shared engine behind a switch, off: until it
switches, it previews with its own engine, the one the recorded values come from, which Rocket Show comes to within 1.
New engine features land in the shared engine only.

**`engine.wasm` is built, not kept in this repository.** The engine is open source since 21 November 2026
([Rocket-Show/lighting-engine](https://github.com/Rocket-Show/lighting-engine), GPL-3.0-or-later, like Rocket Show).
`testing/build-lighting-engine.sh` builds the file at the revision Rocket Show plays with (from a checkout of the engine
next to this repository, or else cloned from GitHub; it needs Rust with the target `wasm32-unknown-unknown`) into
`src/main/resources/lighting-engine`, which is ignored by git, and the build packs it into the jar from there. The Maven
build fails without it, with a message pointing at the script (the enforcer plugin): run it once on a new checkout, and
again when the revision changes. Every CI job which runs Maven builds it first: the unit tests, the jar and the sample
of a release. Where the jar of a release is built, Rust is needed too.

## 8. Risks

| Risk                                                   | What it would change                             | How it is settled                                                       |
| ------------------------------------------------------ | ------------------------------------------------ | ----------------------------------------------------------------------- |
| WebAssembly in the JVM is too slow on SPARK's hardware | Rocket Show takes the native library through JNA | The spike on SPARK's hardware (section 3)                               |
| The runtime's build-time compiler is not ready         | The interpreter if it is fast enough, or JNA     | Settled: the compiler at load time is used, the interpreter is too slow |
| A function of the engine grows too large for the JVM   | It runs in the interpreter, many times slower    | The benchmark of each new revision of the engine (section 3)            |
| A rig too large for a frame of 25 ms                   | Frames dropped: the lights move in steps         | 2,048 pixels take 17 ms on SPARK; more pixels need the engine faster    |
| Single precision is visibly worse somewhere            | A fix in the shared engine                       | The edges of section 5; reports from stages                             |
| Effects of a project follow different audio buses      | Each follows all of them together                | Several streams of audio in the engine, if it is asked for              |

Sources: the lighting engine (`Rocket-Show/lighting-engine`: `concept.md`, `docs/format.md`, `README.md`,
`conformance/DIFFERENCES.md`), [Chicory](https://github.com/dylibso/chicory),
[Endive 1.0](https://foojay.io/today/endive-1-0-wasm/) (29 June 2026). All read on 11 November 2026. Endive 1.2.0 on
Maven Central, 21 November 2026.
