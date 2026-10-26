# Testing Rocket Show

Stability is the most important property of Rocket Show: a device that stops in the middle of a show is worse than a
missing feature. Most of what can go wrong involves real hardware (audio, video, MIDI, DMX, GPIO, updates), so unit
tests alone are not enough.

This document describes how we test new builds and lists the open work. Testing happens in three layers:

| Layer | Runs on | When | Catches |
|---|---|---|---|
| [1. Unit tests and CI](#1-unit-tests-and-ci) | GitHub Actions | Every push and pull request | Logic, serialization and API regressions, broken builds |
| [2. Device self-test](#2-device-self-test) | A single unit | At the factory, first stage of every rig run, on demand | Broken audio, video, MIDI, GPIO, DMX or storage on one unit |
| [3. Hardware-in-the-loop rig](#3-hardware-in-the-loop-rig) | Real units, wired to each other | Every release candidate, soak overnight | Everything that only shows up on real hardware: analog signals, sync, updates, power loss, leaks |

None of the layers contain hardware configurations or model names. What a unit can do is reported by the unit itself
(see [Hardware features](#5-hardware-features)).

Planned layout of this folder:

```
testing/
  README.md     this file
  rig/          controller harness (TODO)
  profiles/     example rig files (TODO)
  media/        test compositions, videos and designer projects for the rig (TODO)
```

## 1. Unit tests and CI

### Today

CI (`.github/workflows/ci.yml`) runs on every push and on pull requests from forks:

- **Unit tests:** `./mvnw -B test -Pcoverage` without the web app build. The summary of the run shows the line
  coverage, the full JaCoCo report is kept as an artifact (`coverage-report`), and so are the reports of failed tests.
  The run fails when the tests cover less than 70 % of the lines or 55 % of the branches (today 74 % and 61 %).
- **Web app unit tests:** the unit tests of the web app in headless Chrome (see [below](#web-app-unit-tests)).
- **Build with web app:** `./mvnw -B package -DskipTests`. The built `rocketshow.jar` is kept as a build artifact for
  14 days, so every commit has a jar to put on a test unit.
- **Web app smoke test:** starts the built jar as a new unit and drives the web app in a browser (see
  [below](#web-app-smoke-test)).
- **Release sample:** on `master` (releases are built from it) and when started by hand, generates the sample files of
  the version in the pom if they are missing (see [Files of earlier releases](#files-of-earlier-releases)).

Dependabot (`.github/dependabot.yml`) proposes updates of Maven, npm and the GitHub actions once a month, one grouped
pull request per ecosystem, so each update goes through CI. Security updates come as soon as they are known, once the
Dependabot alerts and security updates are enabled in the settings of the repository.

The unit tests live in `src/test/java`:

- Update state machine across reboots (`DefaultUpdateServiceTest`) and reading RAUC, also why an installation failed
  (`DefaultRaucServiceTest`)
- MIDI input parsing, mapping and routing (`MidiMessageParserTest`, `MidiMapperTest`, `MidiRouterTest`,
  `DefaultMidiRouterFactoryTest`), MIDI triggers (`DefaultActionMidiExecutionServiceTest`)
- Backup and restore without room for a backup file, broken and failed backups (`DefaultBackupServiceTest`), a backup
  failing during its download in a real web server (`BackupDownloadTest`)
- Files of earlier releases and seed files of new units (`CompatibilityTest`, `DefaultSettingsServiceTest`,
  `DesignerProjectCompatibilityTest`, `SeedFilesTest`, see below)
- The whole app started with each release sample, where every setting is set and every list filled
  (`ReleaseSamplesStartTest`, the API with the latest one in `ReleaseSampleStartTest`)
- The API through its security rules, like the web app and other units call it: what needs a login, an API key or the
  admin, the initial setup, the login throttle, saving the settings like the web app does (`ApiSecurityTest`,
  `SaveSettingsTest`, `LoginThrottleServiceTest`), logging in, the passwords of the admin and the access point, the
  answers to requests it can't fulfill (`AuthApiTest`, `ApiErrorsTest`), the states sent to the web app
  (`DefaultNotificationServiceTest`), selecting and playing compositions with symbols in their name, positions outside
  the set (`TransportApiTest`)
- The monitors of the web app: the latest DMX values and audio levels by bus, MIDI routed while the monitor sends
  (`ActivityMonitorsTest`)
- File names from the web app can't leave their folder (`FileNamesTest`, `FileNameCheckTest`)
- Requests to other units: in the order they were made, encoded names, units that don't answer or don't keep up
  (`RemoteDeviceTest`)
- Compositions and sets: storing them, their durations, keeping the sets up to date (`DefaultCompositionServiceTest`),
  stepping through a set and starting the next composition by itself (`PlayerSetTest`), saving and deleting
  compositions during a show (`ChangeCompositionsWhilePlayingTest`)
- Playing compositions without audio, video or MIDI files: play, pause, seek and stop, their timers and actions,
  samples (`CompositionPlayerTest`, `SamplePlayTest`)
- Playing compositions with media, the pipelines simulated: the end of the media, the actions after it, looping, errors
  of the pipeline (`MediaCompositionPlayerTest`), and on the synchronized units: loading before playing, stopping,
  pausing, samples (`SynchronizedUnitsTest`)
- MIDI devices on a simulated MIDI system of Java: which devices are offered, sending, receiving, finding a device by
  its name and connecting it again (`MidiDevicesTest`)
- Actions of triggers: HTTP requests to other systems, lighting, selecting compositions
  (`DefaultActionExecutionServiceTest`)
- Data on units switched off at the wall: files written in one step, broken files at the start (settings, session,
  compositions, sets, designer projects, fixture profiles), uploads of media and lead sheets in chunks
  (`AtomicFileTest`, `DefaultSettingsServiceTest`, `DefaultSessionServiceTest`, `DefaultCompositionServiceTest`,
  `DesignerProjectsTest`, `FixtureLibraryTest`, `MediaUploadTest`)
- Audio: the generated `.asoundrc`, the output channels each file channel plays on, the sound cards offered for the
  buses (`SystemConfigFilesTest`, `AudioChannelTest`, `AudioDevicesTest`), ALSA reading changed audio settings
  without a restart (`AlsaConfigurationTest`, on devices of the type null)
- Wireless access point: the generated `hostapd.conf` (`SystemConfigFilesTest`)
- System settings: what each save applies and in which order, with the commands recorded instead of run
  (`ApplySystemSettingsTest`)
- DMX: mixing the universes, which universe a value goes to, sending values set while sending, MIDI to DMX
  (`DefaultLightingServiceTest`, `DefaultMidi2LightingConvertServiceTest`), patching the universes in OLA, with a
  simulated OLA (`OlaSetupTest`)
- Lighting designer: presets (`PresetMirrorTest`, `PresetIntensityTest`, `PresetStepsTest`), effect curves and fades
  (`EffectCurveTest`, `TransitionCurveTest`, mirroring the specs of the designer), pixels and pixel groups of fixture
  matrices (`FixturePixelGroupTest`), the DMX values a project sends and the live preview of presets, scenes and the
  timeline (`DesignerDmxTest`)
- Fixture library: searching and reading it like the designer, updating it from rocketshow.net, every mode of a few
  real profiles played by the engine (`FixtureLibraryTest`, profiles in `src/test/resources/fixtures/`)
- GPIO buttons, the reset button and outputs, on the simulated pins of the Pi4J mock plugin (`GpioTest`)
- Reading what the system tells: disk space, LAN, the device information file, the whole output of commands
  (`SystemOutputTest`)
- Scheduler: the next start of each schedule, starting at the time, the clock set after the start
  (`DefaultSchedulerServiceTest`, `SchedulerTimingTest`, `ScheduledCompositionSerializationTest`)
- MIDI timecode: the messages, and the timecodes a receiver puts together while a composition plays
  (`MidiTimecodeMessageFactoryTest`, `DefaultMidiTimecodeServiceTest`)
- Health service (`DefaultHealthServiceTest`), the watchdog of the ready to use units (`DefaultWatchdogServiceTest`) and
  EEPROM version parsing (`DefaultEepromServiceTest`)
- Zipping the logs for the download (`DefaultZipServiceTest`)
- Spring context start (`RocketShowApplicationTests`)

Run them locally without the web app build, with or without the coverage report (`target/site/jacoco`):

```shell
./mvnw test -Dskip.npm -Dskip.installnodenpm
./mvnw test -Pcoverage -Dskip.npm -Dskip.installnodenpm
```

All 446 tests pass without any hardware attached, in about three minutes. One backup test needs an unreadable file,
so it is skipped when running as root (root can read every file). It runs in CI. The tests cover 74 % of the lines and
61 % of the branches, without the libraries shipped with the app (e.g. the OLA client). What's left is mostly the code
building and running the GStreamer pipelines (`CompositionPipelineBuilder`, `CompositionPipeline`) and the video
player, which need GStreamer and real devices.

GStreamer pipelines are not tested here. They need real audio and video devices and belong to the hardware rig
([section 3](#3-hardware-in-the-loop-rig)). Which output channel a file channel plays on is plain logic, so that is
tested here.

### Web app smoke test

`testing/smoke/smoke.mjs` drives the web app of a new unit in Chromium with Playwright, like a user after unpacking a
unit: the initial setup (refusing an access point password which would leave it open), uploading a media file,
creating a composition and a set, opening the composition again, deleting the media file, selecting the composition
to play, opening the designer, renaming the unit in the settings, each checked after a reload. The names contain
symbols which have a meaning in URLs (`&`, `#`, `+`, `%`). Any uncaught error of the web app, API call failing with a
server error or error message of the web app fails
the test. CI runs it against the jar of the build job, with the designer template next to it like on a unit, and keeps
the screenshots of every step and the log of the unit (artifact `smoke-test`). Backups are only offered on SPARK units,
so they are tested on the rig.

Run it locally against a new unit (a fresh directory, because the setup only runs once):

```shell
mkdir /tmp/unit && cp target/rocketshow.jar dist/designer_template.json /tmp/unit && (cd /tmp/unit && java -jar rocketshow.jar &)
cd testing/smoke && npm install && npx playwright install chromium
BASE_URL=http://localhost:8080 node smoke.mjs
```

### Web app unit tests

The web app in `src/main/webapp` has its own unit tests (Karma and Jasmine, `*.spec.ts`), run by CI in headless
Chrome: names with symbols encoded in the requests to the API (`encoded-names.spec.ts`), the access point password of
the setup (`intro.component.spec.ts`), the links to the SPARK page and logging in again after a 401 or 403. The
generated specs of every component and service only checked that it could be created, and none of them compiled
anymore, so they were removed. New specs test behaviour, not that a component exists.

Run them locally (`CHROME_BIN` if Karma doesn't find Chrome or Chromium by itself):

```shell
cd src/main/webapp && npm install --force
npx ng test --watch=false --browsers=ChromeHeadlessCI
```

### Files of earlier releases

After an update, a unit loads the show the previous release saved: settings, compositions, sets, designer projects.
If the new release no longer understands a part of it, that part is lost without a warning, at the latest when the
files are saved again. For example, after a field of the MIDI routings is renamed, every routing of the user is gone.

The tests catch this before a release, but only for what is in the sample files. So each release gets sample files
that use every feature, generated by `ReleaseSampleGenerator` in `src/test/resources/compat/<release>/`:

| File | Content |
|---|---|
| `settings.xml` | Every property filled: several audio buses, MIDI devices, routings to every destination, remote devices, lighting universes, scheduled compositions, MIDI and GPIO triggers with one action of each type, API keys and so on. |
| `session.xml`, `update.xml` | Every property filled. |
| `compositions/Sample composition.xml` | One audio, MIDI and video file, MIDI routings, lead sheets, triggers. |
| `sets/Sample set.xml` | Every property filled. |
| `designer/Sample project.json` | The designer template of the release with more features: two steps looping with a transition, fades, two presets in two scenes, two compositions. |

The generator fills the models of the app generically (`SampleFiller`): every property gets a value, every list one
element of each type it can hold, booleans and enums a value other than their default. So a property added later is in
the samples of the next release without changing the generator. Designer projects are written by the designer in the
web app, not by the models, so the designer sample is built from the designer template instead.

A release folder never changes after the release: it stands for what that release wrote. The generator refuses to
write into an existing folder. Generate the samples of a release once, on the release commit, and commit the folder
(see [Release flow](#7-release-flow)):

```shell
./mvnw test -Dskip.npm -Dskip.installnodenpm -Dtest=ReleaseSampleGenerator -Dsample.release=3.0.1
```

The samples of 3.0.0 were generated from the released code. So the step isn't forgotten, CI generates the samples on
`master` (and when the workflow is started by hand) if the version in the pom has none yet, loads them like the next
releases will and keeps them as artifact `release-sample-<version>` with a warning to commit them.

| Test | Files | Checks |
|---|---|---|
| `CompatibilityTest` | `settings.xml`, `session.xml`, `update.xml`, `compositions/*.xml`, `sets/*.xml` | Loaded strictly: an element the current classes don't read fails the test, because its content would be lost on the next save. Elements dropped on purpose are listed in `StrictXml`, with the reason. |
| `DefaultSettingsServiceTest` | `settings.xml` | Started like after an update: ends at the current settings version, keeps every value of the old file when saving, doesn't change on the next start. Old settings also get the defaults of a new device. |
| `DesignerProjectCompatibilityTest` | `designer/*.json` | Loaded like the app does and played at every scene by the current engine: no errors, and light somewhere. |
| `ReleaseSamplesStartTest` | The whole folder | The whole app starts with it like a unit updated from that release, and loads the compositions, sets and designer projects. |

### Seed files of new units

A new unit starts with the seed files in `dist/`: the example compositions in `dist/defaults/compositions/` and the
designer template `dist/designer_template.json`, the start of every new designer project. A new unit also saves the
default settings on its first start. Seed files only need to work with the latest release, so they are tested where
they are, not kept per release:

- The example compositions load strictly (`SeedFilesTest`).
- The designer template uses the example compositions (`SeedFilesTest`), is in the project version of the designer the
  web app comes with and plays with light and without errors (`DesignerProjectCompatibilityTest`).
- The settings of a new unit load strictly and don't change on the next start (`DefaultSettingsServiceTest`).

The designer migrations are tested with a project in version 2 and the same project in version 7, in
`src/test/resources/designer/`.

### TODO

- [x] GitHub Actions workflow running the unit tests on every push and pull request.
- [x] Build the web app in CI as well, so frontend build breakage is caught.
- [x] Test the update state machine in `DefaultUpdateService` with `RaucService`, `RebootService` and `HealthService`
  mocked. Cases:
  - [x] Healthy or degraded first boot in the new slot: slot is marked good, state is `FINISHED` without error.
  - [x] Unhealthy first boot in the new slot: state is `FALLING_BACK`, device reboots.
  - [x] Fell back to the original slot: state is `FINISHED` with an error, nothing is marked good.
  - [x] Booted after `FALLING_BACK`: error "Update failed. Reverted original state.".
  - [x] Interrupted during `UPDATING`: error, no endless wait.
  - [x] `rauc install` fails: error, no reboot.
  - [x] `rauc status` returns no slot: the app still starts.
  - [x] Remote version equals current version: error, nothing installed.
  - [x] Update started by an older release, which did not store the original slot: finishes in the new slot, reports
    the fallback in the original slot.
- [x] Compatibility test for files of earlier releases.
  - [x] Designer projects, played by the current engine, and their migrations.
  - [x] Settings, started through `DefaultSettingsService`.
  - [x] Sample files with every feature, generated per release (`ReleaseSampleGenerator`). Samples of 3.0.0.
  - [x] Seed files of new units, for the latest release.
  - [x] Generate the samples in CI when a release is built, so the step can't be forgotten (on `master`, to be
    committed by hand).
  - [x] The fixture library (`fixtures/`, used by the designer's fixture search).
- [x] `MidiMessageParser`: messages split across reads, running status, real-time bytes inside messages and SysEx,
  SysEx without end byte.
- [x] MIDI mapping (`MidiMapper`): channel map, offsets, global mapping.
- [x] MIDI routing (`MidiRouter`, `DefaultMidiRouterFactory`): every destination, own mapping per routing, global
  mapping, monitor, changed routings without a restart.
- [x] Backup and restore round trip, broken and incomplete backups, failed backup creation (`BackupService`).
- [x] `RocketShowApplicationTests` passes without GStreamer, OLA or GPIO hardware, checked by CI.
- [x] MIDI triggers, including note on with velocity 0.
- [x] Security rules of the API (MockMvc through the filter chain) and saving the settings like the web app does.
- [x] File names from the web app (path traversal), zip entries of the fixture library.
- [x] Remote units which don't answer.
- [x] Generated `.asoundrc` and `hostapd.conf`, output channels of audio buses.
- [x] DMX mixing, universe mapping and MIDI to DMX.
- [x] The whole app started with the full release sample.
- [x] Designer effect curves, fades and pixel groups, mirroring the specs of the designer.
- [x] Compositions and sets: storing, durations, keeping the sets up to date.
- [x] Parsing the output of the system: RAUC, disk space, LAN, device information, command output while debugging.
- [x] The player's set logic: next, previous, empty and deleted sets, auto select.
- [x] Coverage (JaCoCo) in CI, Dependabot, web app smoke test against the built jar.
  - [x] Start the app with the samples of every release, not only the latest.
  - [x] More flows in the smoke test: sets, the designer, uploading media. Backups are SPARK only, see the rig.
  - [x] A minimum coverage for the whole app (raised to 70 % and 55 % with the second round of tests).
  - [ ] A minimum coverage per package, once the numbers are stable.
- [x] Playing compositions without files: timers, seeking, pausing, samples.
- [x] Actions of triggers, including HTTP requests to systems which don't answer.
- [x] The DMX output of designer projects and every mode of real fixture profiles.
- [x] The API: login, setup, passwords, error answers, states sent to the web app.
- [x] Data files and uploads on units switched off at the wall, broken files at the start.
- [x] Watchdog, MIDI timecode, scheduler timing, zipping the logs.
- [x] GPIO with the Pi4J mock plugin.
- [x] The player with media and synchronized units, the transport API with symbols in names.
- [x] The monitors of the web app, MIDI devices on a simulated MIDI system, the sound cards offered.
- [x] Patching the universes in OLA, the live preview of the designer, applying the system settings.
- [x] The unit tests of the web app, in CI.
- [x] Changes during a show: saving compositions, audio settings, MIDI to other units in order.
  - [ ] More specs for the parts of the web app with logic, e.g. the settings forms and the composition editor.
- [ ] When the web app gets the designer writing project version 8 (`@rocketshow/designer`), raise
  `dist/designer_template.json` to version 8 (halving the amplitude of its sine) and
  `WEB_APP_DESIGNER_PROJECT_VERSION` in `DesignerProjectCompatibilityTest`. Until then the app migrates the template
  like any older project, with the same light.
- [ ] The GStreamer pipelines a composition builds, with GStreamer installed in CI and test sources and sinks instead
  of the devices.

## 2. Device self-test

A unit checks its own hardware, with nothing attached except optional loopback cables. It runs through
`POST /api/system/test`, which calls `HealthService.testSystem()`. Today that method is an empty stub
(`src/main/java/com/ascargon/rocketshow/health/DefaultHealthService.java`).

### Where it is used

- **At the factory, during provisioning.** The endpoint is reachable without login as long as the initial setup is not
  completed (`SecurityConfig.java:72`), like the device information and update endpoints. Sequence: flash the image,
  store the device information, run the self-test on a fixture with loopback cables, keep the result with the serial
  number, ship.
- **As the first stage of every rig run.** A unit that fails its self-test is not tested further, so rig failures
  point at real problems instead of a loose cable.
- **On demand by an admin**, e.g. when helping a user. It plays test tones and switches outputs, so it never runs by
  itself: not at boot, not after an update.

### What it proves and what it does not

It proves everything up to the driver: the pipelines start, the devices open, signals leave the software, MIDI and
GPIO loopbacks come back. It cannot prove that analog audio sounds right, that an HDMI picture appears or that the DMX
signal is electrically correct. That needs external measurement: the rig ([section 3](#3-hardware-in-the-loop-rig))
or a factory fixture.

It does not use the default composition or any user files: a factory unit has no user content, and a user's default
composition can be anything. All signals are generated (GStreamer `audiotestsrc` and `videotestsrc`, fixed MIDI and DMX
patterns), so the result does not depend on media files.

### Flow

1. Refuse with `409 Conflict` while a composition other than the default composition is playing. The self-test never
   interrupts a show.
2. Stop the default composition.
3. Suspend MIDI triggers, GPIO triggers and MIDI forwarding to remote devices, so test signals cannot start
   compositions or actions, here or on other units.
4. Run the checks below. A check only runs if the unit has the hardware ([section 5](#5-hardware-features)). Otherwise
   it is `SKIPPED` with the reason.
5. Restore, even if a check failed or threw: stop the test pipelines, set the test GPIO outputs low, send MIDI "all
   notes off", zero the test DMX values, resume the triggers, restart the default composition.
6. Write every check to the log (so it shows up in the downloaded logs) and return the result.

The call is synchronous and takes less than a minute.

### Checks

| Check | What it does | Passes when |
|---|---|---|
| `health` | Reads the health status | Severity is `OK` or `DEGRADED`. The reasons go into the details. |
| `storage` | Writes, reads back and deletes a 1 MB test file in the data directory | The content matches |
| `audio` | Plays a test tone on every channel of every audio output for 3 seconds, through the same sink as a composition. Measures with the pipeline's `level` element. | The pipeline reaches `PLAYING`, no error on the bus, every channel shows the tone's level (±3 dB) |
| `video` | If a display is connected: plays `videotestsrc` on the video output for 3 seconds | The pipeline reaches `PLAYING`, no error on the bus |
| `midi-out` | Sends a fixed burst of 200 messages (note on/off, program change, control change) on every MIDI out device | Sending succeeds |
| `midi-loopback` | Listens on every MIDI in device during each burst | Every message arrives once, in order, unchanged. Reports which out device arrived on which in device. |
| `gpio-out` | Switches the GPIO outputs configured in the settings high and low once per second for 5 seconds (visible on a fixture LED). On a unit without GPIO settings (factory): every user pin. Never touches reserved pins or pins configured as inputs. | The state read back matches |
| `gpio-loopback` | For each output → input pair given in the request: switches the output, reads the input. Pairs with a reserved pin are refused. | The input follows the output |
| `dmx` | Sends a ramp on every patched output port for 3 seconds, then zero | `olad` is running, the ports are patched, no error from OLA |

A loopback that receives nothing is `SKIPPED` ("no loopback cable"), unless the request expects it. Then it is
`FAILED`. This way the same test runs on a fixture with cables and on a unit without them.

### Request and result

```http
POST /api/system/test
Content-Type: application/json

{
  "expect": ["midi-loopback", "gpio-loopback"],
  "gpioLoopback": [{ "out": 22, "in": 27 }]
}
```

Both fields are optional. An empty request runs all checks the unit has hardware for and skips loopbacks that receive
nothing.

```json
{
  "result": "FAILED",
  "startedAt": "2026-10-24T15:37:06Z",
  "durationMillis": 21400,
  "softwareVersion": "3.0.0",
  "serial": "…",
  "checks": [
    { "name": "health", "result": "PASSED", "details": "DEGRADED: EEPROM version date … is older than …" },
    { "name": "audio", "target": "8 channels", "result": "PASSED", "details": "-12.1 to -12.4 dBFS" },
    { "name": "video", "result": "SKIPPED", "details": "No display connected" },
    { "name": "midi-loopback", "target": "MIDI out 1 → MIDI in 1", "result": "FAILED", "details": "196 of 200 messages received" }
  ]
}
```

`result` is `FAILED` if any check failed, otherwise `PASSED`. Skipped checks do not fail the run.

### TODO

- [ ] Result model (`SelfTestResult`, `SelfTestCheck`) returned by `POST /api/system/test`, `409` while playing.
- [ ] Suspend and resume MIDI triggers, GPIO triggers and MIDI forwarding to remote devices.
- [ ] Checks: `health`, `storage`, `audio`, `video`, `midi-out`, `midi-loopback`, `gpio-out`, `gpio-loopback`, `dmx`.
- [ ] Restore step that always runs, also after an exception.
- [ ] Unit tests for the orchestration with mocked services: skip logic, `expect`, `409`, restore after a failing
  check.
- [ ] Web app: "Run self-test" for admins in the system settings, with a warning that it plays test tones and switches
  outputs, showing the result table.
- [ ] Document the factory provisioning sequence.

## 3. Hardware-in-the-loop rig

A controller (laptop or spare Raspberry Pi) drives two or more Rocket Show units through the REST API and WebSockets
and measures what physically comes out of them. Almost everything a test needs is already exposed:

- REST (`/api/...`, authenticated with the `X-API-Key` header): transport, sets, compositions, MIDI send, settings,
  backup and restore, update, health, self-test.
- WebSockets: `/api/state`, `/api/activity/midi`, `/api/activity/audio`, `/api/activity/lighting`,
  `/api/update/state`.

```
                 ┌──────────── Controller (laptop or spare Pi) ────────────┐
                 │  test harness · USB audio in · HDMI capture · olad      │
                 └──┬───────────────┬──────────────┬──────────────┬────────┘
             REST + WebSockets   audio L        audio R         HDMI
                    │               │              │              │
           ┌────────┴──────┐  MIDI A→B, B→A  ┌─────┴─────────┐    │
           │ Unit A (main) ├────────────────►│ Unit B (sync) │    │
           │               │◄────────────────┤               │    │
           │               │ GPIO out → in   │               │    │
           └───────┬───────┴─────────────────┴───────┬───────┘    │
              smart plug                        smart plug        │
                   └────────── HDMI of unit A ───────────────────-┘
```

| Link | Wiring | Driven by | Checked by |
|---|---|---|---|
| MIDI | A out → B in, B out → A in (DIN or USB) | `POST /api/midi/send-message`, MIDI actions in compositions | `/api/activity/midi` on the other unit, MIDI triggered actions |
| GPIO | A user pin → B user pin, common ground, ~1 kΩ series resistor | GPIO action in a composition on A | B's GPIO trigger starts a composition, visible in B's `/api/state` |
| Audio | A → left, B → right input of a USB audio interface on the controller | Test composition with a different sine tone per channel and clicks at known times | Signal present, channel routing, dropouts, A↔B sync offset in ms (cross-correlation) |
| Video | HDMI → USB HDMI capture stick | Test video with a frame counter and a flash plus beep | Frames are not black, audio/video offset |
| DMX | Art-Net or sACN to `olad` on the controller, or a USB DMX interface that can receive | Lighting actions and designer projects | DMX values at known times (`olad` HTTP API, e.g. `http://controller:9090/get_dmx?u=1`) |
| Power | One smart plug per unit with a local HTTP API (Shelly, Tasmota) | Harness | Power cuts at chosen or random moments |
| Network | Main unit A synchronizes unit B (remote device with "synchronize") | Transport on A | B follows A, see audio sync offset |

### TODO

- [ ] Set up the rig: controller, 2-3 units, USB audio interface, HDMI capture stick, smart plugs, MIDI and GPIO
  cables.
- [ ] Harness in `testing/rig/`. Proposed: Python with pytest (numpy for the audio analysis, WebSocket client, runs
  on a laptop or a Pi).
- [ ] REST and WebSocket client with API key support.
- [ ] Run the self-test ([section 2](#2-device-self-test)) on every unit as the first stage.
- [ ] Test media in `testing/media/`: sine per channel, click track, flash plus beep video, DMX designer project with
  known values.
- [ ] MIDI loopback tests (both directions, note on, program change, MIDI triggered actions, MIDI timecode).
- [ ] GPIO loopback tests (output action on A, input trigger on B).
- [ ] Audio capture and analysis: presence, routing, dropouts, A↔B offset. Track the offset per build.
- [ ] Video capture and analysis: non-black frames, audio/video offset.
- [ ] DMX capture through `olad` on the controller.
- [ ] GStreamer pipelines (`CompositionPipelineBuilder`): compositions with several audio buses, channel mapping and
  mix matrix, video and MIDI files. Checked with the audio, video and DMX capture.
- [ ] Smart plug control for power cut tests.
- [ ] Test report per run (pass/fail per test, sync offsets, soak graphs), stored per build.

## 4. Rig configuration

The controller holds a rig file. It only describes the rig: which units take part and which cables connect them. It
contains no hardware configuration: what a unit has comes from the unit itself ([section 5](#5-hardware-features)).

### 4.1 Rig file

```yaml
units:
  a: { host: 192.168.1.50, api_key_env: RS_A_KEY, power: shelly://192.168.1.60 }
  b: { host: 192.168.1.51, api_key_env: RS_B_KEY, power: shelly://192.168.1.61 }
capture:
  audio: { device: "USB Audio", channels: 2 }
  video: { device: /dev/video0 }
links:
  - midi:  a.out -> b.in
  - midi:  b.out -> a.in
  - gpio:  a.22 -> b.27
  - audio: a.out[1,2] -> capture.in[1]
  - audio: b.out[1,2] -> capture.in[2]
  - video: a.hdmi -> capture.video
  - dmx:   a.artnet[1] -> controller.olad
```

MIDI, audio and DMX links can also be detected instead of declared: send a unique note, tone or DMX value from each
output and see where it arrives. Then the rig file shrinks to the list of units and the GPIO links. GPIO links are not
probed, because switching unknown pins on a unit is not safe.

### 4.2 Check against the units

Before a run, the harness asks every unit for its features ([section 5](#5-hardware-features)) and checks the rig file
against them. A link to a MIDI port, audio channel, DMX port or GPIO pin that the unit does not report fails the run
early with a clear message instead of a confusing timeout later. Tests that need a feature a unit does not have are
skipped, not failed.

### 4.3 Baseline settings

Before a run, the harness puts every unit into a known state with `POST /api/system/settings` or by restoring a test
backup (`POST /api/system/restore-backup`). Afterwards it restores the unit's original settings. Without this, results
depend on whatever was configured last and the tests become flaky.

The rig file is not stored in Rocket Show's own settings: test configuration must never ship to devices in the field.

### TODO

- [ ] Rig file format and loader.
- [ ] Check the rig file against the features the units report.
- [ ] Link detection for MIDI, audio and DMX.
- [ ] Baseline settings, backup of the original settings and restore after the run.
- [ ] Example rig file in `testing/profiles/`.

## 5. Hardware features

There can be several SPARK models and revisions, and the community edition runs on any Raspberry Pi with any USB
interfaces. So neither the self-test, the harness nor the tests know about models. Instead:

1. Every unit reports its own features.
2. Every test declares the features it needs, e.g. `midi-out`, `midi-in`, `gpio`, `ab-update`.
3. A test runs on a unit only when the unit has all of them. Otherwise it is skipped with the reason.

### Where the features come from

Most features can be discovered at runtime. Only a few physical facts cannot, and those come from a hardware
description on the unit itself, never from the harness.

| Feature | Source |
|---|---|
| Audio outputs and channel count | Discovered: `GET /api/audio/devices`, `GET /api/audio/max-channels` |
| MIDI in and out devices | Discovered: `GET /api/midi/in-devices`, `GET /api/midi/out-devices` |
| DMX and Art-Net output ports | Discovered: `GET /api/lighting/ola-output-ports` |
| Display connected | Discovered: `HdmiService.isConnected()` (not exposed through the API yet) |
| A/B updates with rollback | Discovered: `raucSlot` is set in `GET /api/system/health` |
| Watchdog, read-only root file system | Hardware description |
| GPIO pins free for users and tests | Hardware description |
| Reserved GPIO pins, e.g. a reset button | Hardware description |

### Hardware description

A small file per model and revision, delivered with the image or written during provisioning next to
`/provision/device-information.conf`. A new model or revision only needs a new file, no code change. Format to be
defined, for example:

```yaml
features: [watchdog, read-only-root]
gpio:
  user: [22, 23, 24, 27]
  reserved:
    - { pin: 17, use: reset-button }   # held low for 3 s = factory reset
```

A unit without a description (e.g. the community edition on a plain Raspberry Pi) reports its discovered features
only. Its GPIO pins for the rig come from the rig file.

`GET /api/system/hardware` returns the merged result: discovered features plus the description. The existing
`Capabilities` class (`src/main/java/com/ascargon/rocketshow/settings/Capabilities.java`) is not exposed through any
endpoint today and could become this.

The app itself should use the same description. Today its hardware specifics are hard-coded behind
`DeviceInformation.isAvailable()`: the reset button is always GPIO 17 (`DefaultRaspberryGpioInService.java:69-75`), and
the watchdog, the read-only file system and the A/B updates are switched on for every provisioned unit. A model with a
different reset pin would need a code change.

### Safety rules

- The self-test and the harness only switch GPIO pins listed as user pins, or declared in the rig file for units
  without a description. Reserved pins are never touched. A reset button held low for 3 seconds factory resets the
  unit.
- The rig loader refuses a rig file that wires anything to a reserved pin.
- Use a series resistor on GPIO links, in case a pin is configured as output on both sides.
- Use units that are not needed for a real show. Settings are overwritten during a run.

### TODO

- [ ] Define the hardware description format and where it lives (image or provisioning).
- [ ] `GET /api/system/hardware`: discovered features plus the description.
- [ ] Expose whether a display is connected.
- [ ] Read reserved pins (reset button) from the description instead of hard-coding GPIO 17.
- [ ] Turn the `isAvailable()` checks that are really features (watchdog, read-only root, A/B updates) into feature
  checks.
- [ ] Harness: feature tags on tests, skip with reason.
- [ ] Rig loader: refuse wiring to reserved pins.
- [ ] Document how community users submit rig files and results for their hardware.

## 6. Test scenarios per release candidate

### 6.1 Update paths

- [ ] Update every rig unit from each previous release to the candidate (`POST /api/system/update?testBranch=true`),
  not only fresh installs.
- [ ] Before updating, restore real show backups (our own shows, customer backups with permission).
- [ ] After the update, check version, RAUC slot, health, settings and compositions, and run the self-test.
- [ ] Fresh image install and first-boot setup, for every image we ship.

### 6.2 Forced update failures (units with A/B updates)

These are the paths that brick units in the field, and nobody runs them by hand.

- [ ] Install a deliberately broken bundle (app exits at start): the unit must fall back to the old slot and report
  the failure.
- [ ] Cut power during `rauc install`.
- [ ] Cut power during the first boot of the new slot.
- [ ] Unhealthy first boot (e.g. health reports `FAIL_REBOOT_DEVICE`): falls back.

### 6.3 Show scenarios

- [ ] Play a full set end to end, check audio, video, DMX and MIDI against the expected timing.
- [ ] Spam play, stop, next and previous.
- [ ] Seek while playing and while paused.
- [ ] Parallel playback with `play-as-sample`.
- [ ] Default composition starts after stop.
- [ ] Scheduler starts a composition at the planned time.
- [ ] Settings save and reload while idle.
- [ ] Backup and restore.
- [ ] Change the sound card of a bus, the rate and the ALSA period and buffer while idle and while the default
  composition plays: audio plays on the new settings without a restart, without sound artifacts.

### 6.4 Chaos

- [ ] Unplug and replug USB MIDI and USB DMX during a show.
- [ ] Reboot unit B during a show on unit A.
- [ ] Freeze unit B (`kill -STOP` the Java process), then press play on unit A.
- [ ] Pull the network cable between the units during a show.
- [ ] Cut power during a show and check the unit comes back to a usable state.

### 6.5 Soak

- [ ] Loop a set for 24-72 hours.
- [ ] Sample health, Java memory, open file handles, thread count and temperature. Leaks show up as a trend, not as a
  failure.
- [ ] Fill the unused `freeMemory` field of `HealthStatus`.
- [ ] Run once in a closed case to check thermals.

## 7. Release flow

1. CI is green on the commit.
2. Commit the sample files of the release, generated by CI on `master` (see
   [Files of earlier releases](#files-of-earlier-releases)).
3. The build is published to the test branch.
4. Rig units update through the test branch.
5. The rig suite runs, starting with the self-test (target: under an hour).
6. Soak overnight.
7. Promote to stable.

### TODO

- [ ] Automate steps 4-6 on the controller.
- [ ] Publish the test report with the release.

## 8. Known issues

Found by reading the code and by the tests. Each one gets a unit test or rig test before it is fixed.

### Fixed

- [x] **A failed update was reported as successful.** `UpdateState.originalRaucSlot` was read after the reboot but
  never set. When the device fell back to the original slot, the update was reported as `FINISHED` without an error
  and the old slot was marked good. Now the original slot is stored and the running version is compared as well
  (`DefaultUpdateServiceTest`). Updates that start from a release before the fix can still be misreported when they
  fall back, because the old release runs that check.
- [x] **The app did not start if `rauc status` returned no slot after an update.** (`DefaultUpdateServiceTest`)
- [x] **Serial MIDI input lost or garbled messages.** Messages with running status were garbled or dropped, a
  real-time byte (e.g. MIDI clock) inside a message replaced the message, and a SysEx without its end byte swallowed
  all following input (`MidiMessageParserTest`).
- [x] **Restoring a broken backup deleted all data.** An incomplete or broken upload deleted the settings,
  compositions, sets and media, unpacked nothing and rebooted. Now the backup is checked first and the restore is
  refused with an error (`DefaultBackupServiceTest`).
- [x] **Creating a backup ignored the exit code of `tar`.** A failed backup (e.g. disk full) was offered for download
  as if it was complete (`DefaultBackupServiceTest`).
- [x] **The global MIDI channel and note offsets had no effect.** A routing's own offsets (`0` by default) replaced
  them, and the routings of the live MIDI input had no parent at all. Now the offsets add up: the global offsets apply
  to every routing, plus the routing's own. Override parent was removed, the global mapping always applies
  (`MidiMapperTest`, `MidiRouterTest`).
- [x] **The MIDI mapping of a routing leaked into the other routings.** All routings mapped the same message object,
  so a routing got the offsets of the routings before it, and the caller's message changed as well. Each routing gets
  its own copy now (`MidiRouterTest`).
- [x] **Changes to the MIDI routings and the global MIDI mapping applied only after a restart.** The routers of the
  MIDI input, of remote MIDI and of actions were created once at startup. Now the settings service publishes an event
  when the routings or the global mapping change, and these routers switch to the new ones
  (`DefaultMidiRouterFactoryTest`, `DefaultSettingsServiceTest`).
- [x] **USB MIDI input was routed twice.** The device was also connected directly to a single routing, so with one
  routing every message arrived twice (e.g. two note on). Found while fixing the routing changes, from reading the
  code. Now USB input is routed once, like serial input.
- [x] **Releasing a key fired MIDI triggers again.** Many devices send a note on with velocity 0 instead of a note off,
  which matched note on triggers (`DefaultActionMidiExecutionServiceTest`).
- [x] **File names from the web app could leave their folder.** A name like `../../settings.xml` could read, overwrite
  or delete any file the app can reach (logged in users only). Names of media files, lead sheets, compositions, sets,
  designer projects and fixtures are checked now, and the fixture library is only extracted into its folder
  (`FileNameCheckTest`).
- [x] **A unit which didn't answer blocked its connections for good.** `RemoteDevice` had no read timeout: a unit
  accepting a request without answering (e.g. it lost its power mid request) kept the request open forever, and after
  two of them all connections to that unit were used up, so every play waited a minute. Requests now give up after
  30 seconds (`RemoteDeviceTest`).
- [x] **Playing a composition as sample on other units failed for names with a space.** The name wasn't URL encoded
  (`RemoteDeviceTest`).
- [x] **An audio bus without device broke the audio of all buses.** "No audio device" is offered for a bus, but then
  the `.asoundrc` wasn't written and playing audio failed (`SystemConfigFilesTest`, `AudioChannelTest`).
- [x] **Two audio buses with the same name played on the same channels.** Buses are told apart by their uuid now
  (`AudioChannelTest`).
- [x] **A DMX value set while the universe was being sent was lost.** E.g. the last value of a fade or a MIDI note off
  stayed unsent until the next change (`DefaultLightingServiceTest`).
- [x] **The app didn't start with GPIO enabled when GPIO couldn't be initialized.** E.g. on a model without supported
  GPIO, after restoring a backup on another model or after a kernel update renumbering the GPIO chip. GPIO couldn't
  even be disabled anymore, because the web app wasn't reachable. Found by starting the app with the release sample
  (`ReleaseSampleStartTest`).
- [x] **Pixel groups by position lit other pixels on stage than in the designer.** All three coordinates of a pixel
  were written to x (`FixturePixelGroupTest`).
- [x] **Deleting a composition failed while none was selected.** E.g. with an empty set loaded
  (`DefaultCompositionServiceTest`).
- [x] **Debug logging took away parts of the output of commands.** E.g. the slot RAUC booted from, which decides
  whether an update succeeded (`SystemOutputTest`).
- [x] **Every play of a composition left threads behind.** The timers of a composition were never stopped: 40 threads
  after 10 plays, until the unit ran out of them (`CompositionPlayerTest`).
- [x] **Seeking fired the actions of a paused or stopped composition**, failed on a composition never played and didn't
  move its end. Pausing a stopped composition broke loading the next one, playing a composition without files failed
  (`CompositionPlayerTest`).
- [x] **Samples stopped playing after 20 plays.** Samples without files were never removed from the playing samples,
  samples with files were never stopped (`SamplePlayTest`).
- [x] **An HTTP action held up all triggers.** It ran on the thread of the trigger, waited a minute to connect and
  forever for the answer, so a slow system delayed MIDI and GPIO triggers and a hanging one blocked them. An answer
  without content (e.g. 204) failed the actions after it. HTTP actions run on their own thread with timeouts now
  (`DefaultActionExecutionServiceTest`).
- [x] **Selecting a composition by name didn't move the position in the set.** "Next" continued from the old position
  (`PlayerSetTest`).
- [x] **Fixtures with unused channels got their values on the wrong addresses.** Every channel after an unused one
  (`null` in the library, 48 modes) was sent one address too early, e.g. blue on the strobe channel. A profile using a
  wheel it doesn't define (e.g. `dts/xr4-spot`) left the whole project dark, a default value with a fraction failed to
  load the project, and profiles straight from the library failed to load (`FixtureLibraryTest`, `DesignerDmxTest`).
- [x] **The wireless access point was left open.** `hostapd` only protects it with a passphrase of 8 to 63 characters
  (a longer one stops it). The setup took 6 characters and used the admin password by default, and didn't compare the
  repeated Wi-Fi password. The setup, the settings and the API refuse other lengths now, and the health report warns
  about an access point left open by an earlier release (`AuthApiTest`, `DefaultHealthServiceTest`, smoke test).
- [x] **No settings could be saved after trying a short access point password in the setup.** The refused passphrase
  was stored even after disabling the access point, and every save was refused because of it, also on units with a
  short passphrase from an earlier release and the access point off. The passphrase only counts for an enabled access
  point now (`AuthApiTest`, found by the smoke test).
- [x] **Logging in kept the session from before** (session fixation), and the admin password could be changed to an
  empty one (`AuthApiTest`).
- [x] **The web app could show an older state.** States sent from several threads at once overtook each other
  (`DefaultNotificationServiceTest`). The state also failed when no set was loaded.
- [x] **Data was lost when a unit was switched off while saving.** Settings, session, compositions, sets, designer
  projects and the update state were written in place, so a power cut left half a file. They are written to a hidden
  file first and moved in place now (`AtomicFileTest`). Settings which can't be read are kept as
  `settings.xml.broken` instead of being replaced by the defaults without a trace (`DefaultSettingsServiceTest`).
- [x] **One broken file stopped others.** A broken session left the player without one, a broken designer project
  stopped the app from starting, a broken fixture profile emptied the fixture search (`DefaultSessionServiceTest`,
  `DesignerProjectsTest`, `FixtureLibraryTest`).
- [x] **An upload replaced the file while uploading.** An interrupted upload left half a file, and a composition played
  meanwhile played a part of it. Uploads go to a hidden file first now (`MediaUploadTest`).
- [x] **A degraded unit ran out of memory within about 20 minutes.** The watchdog logged the health as an error every
  30 seconds, and the health lists the latest errors, so each text contained the ones before: 3.4 MB after 10
  minutes, growing by about 60 % on each check. It logs a change once as a warning now (`DefaultWatchdogServiceTest`).
- [x] **MIDI timecode jumped by a second at each new second.** Each quarter frame carried the position of its own
  moment, so a receiver put timecodes together from different frames. A quarter frame of a stopped timecode could
  still be sent and shift the next start (`DefaultMidiTimecodeServiceTest`).
- [x] **An interrupted download of the fixture library deleted the library.** The update deleted it before unpacking
  the download, which ran without timeouts at every start. The new library replaces the old one once complete now
  (`FixtureLibraryTest`).
- [x] **Scheduled compositions mostly started twice.** A daily, weekly, monthly or yearly start ran up to a millisecond
  early and planned itself again (8 of 12 starts in a test). A unit without real time clock planned its starts with
  the time before setting the clock from the internet, so a show could start at night. Starts are planned after the
  last one and again when the clock is set now (`SchedulerTimingTest`).
- [x] **Changes to GPIO buttons and outputs applied only after a restart, without the web app saying so.** A changed
  action of a button ran the old one, a removed button still fired (`GpioTest`).
- [x] **A missing file was answered with a server error and logged as an error.** E.g. the demo audio of the designer
  template after deleting it, each time the designer opened. It's answered with 404 now (`ApiErrorsTest`, found by the
  smoke test).
- [x] **The default composition started between two compositions of a set.** When a composition started the next one
  by itself, the default composition (e.g. the lighting between songs) was loaded, started and stopped again right
  away in between (`PlayerSetTest`).
- [x] **Names with `&`, `#`, `+` or `%` broke the web app.** It put the names of compositions, sets, media files and
  lead sheets into its requests without encoding them, so they arrived cut off or changed: selecting, opening or
  loading such a composition failed, and deleting the media file "Rock+Roll" could delete "Rock Roll" instead
  (`encoded-names.spec.ts`, `TransportApiTest`, smoke test).
- [x] **Selecting a position outside the set was accepted.** E.g. from a web app showing a set which changed meanwhile:
  the set stood at a position it doesn't have, no composition was selected and "previous" failed. It's refused now,
  also without a set loaded. Loading a composition the unit doesn't have (e.g. on a synchronized unit) is a warning in
  the log now instead of a server error (`TransportApiTest`).
- [x] **The monitors of the web app showed old values and broke MIDI routing.** The DMX monitor sent the first change
  of every 50 ms instead of the last one, so the end of a fade or a blackout never showed. The MIDI monitor collected
  messages from several threads without a lock, which threw errors back into the MIDI routing, so the message wasn't
  routed. The audio monitor started a thread per level message, and the levels arrived out of order with a slow web
  app (`ActivityMonitorsTest`).
- [x] **MIDI devices on USB never connected.** Looking up the device of the settings returned "not found" whenever a
  device was set, so MIDI in and out only worked over serial ports (`MidiDevicesTest`).
- [x] **MIDI out kept every message sent in memory.** It asked the device for a new receiver for each message, and Java
  keeps every receiver until the device is closed: with MIDI clock 48 per second, about 350,000 in a two-hour show.
  MIDI out keeps one receiver per connection now (`MidiDevicesTest`).
- [x] **Connecting the MIDI devices again did nothing for serial ports**, and each MIDI message received over a serial
  port was logged as info, e.g. 48 lines per second of MIDI clock (`MidiDevicesTest`).
- [x] **Saving any setting restarted the access point and interrupted the DMX output.** Every save applied all system
  settings again: it restarted the access point (disconnecting a tablet controlling the unit through it), ran the LAN
  and connection mode scripts and patched the DMX universes in OLA again, e.g. when changing a MIDI routing during a
  show. These run when they changed now, and a patch of OLA matching the settings is kept. The commands ran at the
  same time, so e.g. the access point could restart before its country was set. They run one after the other now,
  and failures are logged with their output (`ApplySystemSettingsTest`, `OlaSetupTest`).
- [x] **Requests to other units arrived in another order than sent.** Play, pause, stop and MIDI messages each ran on
  a new thread, so e.g. a note off could arrive before its note on and leave the note hanging. Each unit gets its
  requests one after the other now. A unit which doesn't answer holds up the requests after it (each up to the
  timeouts): at most 1000 wait, more are dropped with a warning (`RemoteDeviceTest`).
- [x] **Saving a composition stopped the show.** Saving any composition stopped the one playing and jumped back to the
  start of the set, e.g. when fixing the next song, and deleting one jumped back as well. The composition playing
  plays on now, the set keeps its position, a saved composition playing is taken once it stops, and only new or
  changed files are analyzed again (`ChangeCompositionsWhilePlayingTest`).
- [x] **A failed update only showed "RAUC exited with exit code 1".** It shows the error RAUC reported now, e.g. a
  wrong signature (`DefaultRaucServiceTest`).
- [x] **Backups needed half of the disk free.** The backup file was written to the unit before the download. Restoring
  stored the uploaded backup and unpacked it next to it, so a new unit needed room for it twice, and a failed unpacking
  (e.g. a full disk) went unnoticed: the unit restarted with a part of the backup. A backup is written straight into
  the download now and breaks it off when it fails, a restored backup is unpacked aside while it's uploaded and
  replaces the data only when it's complete (`DefaultBackupServiceTest`, `BackupDownloadTest`).
- [x] **Changed audio settings needed a restart.** ALSA reads the `.asoundrc` once in a process, and the sound cards
  kept open against sound artifacts were only opened at the start. ALSA reads it again after a change now, and the
  sound cards are opened again (`AlsaConfigurationTest`, `ApplySystemSettingsTest`). Needs the rig to confirm it on
  real sound cards (see the show scenarios).
- [x] **Sine, triangle and sawtooth effects swung over half of their amplitude, the square over all of it.** At the
  default amplitude of 1 around the middle, a sine moved a channel between 64 and 191, a square between 0 and 255, in
  the designer as well. Every curve swings over its whole amplitude now, in the app and in the designer
  (Rocket-Show/show-designer `claude/curve-amplitude`). Designer projects are version 8: older ones get half the
  amplitude for these curves, so they look exactly the same (`EffectCurveTest`, `DesignerDmxTest`,
  `DesignerProjectCompatibilityTest`).

### Accepted

- **2.x compositions lose their audio bus.** They assign audio files to a bus by name (`<outputBus>`), 3.0 only reads
  the bus UUID, so all audio files of such a composition play on the first bus.
- **Settings without a version end up at version 3, while the current version is 2.** Only settings of very old
  releases are affected. Every start logs an error, the settings are kept, and the old MIDI controls are lost.

### Open

- [ ] **The post-update check only proves the app starts.** `finishUpdate()` accepts `OK` and `DEGRADED`. A build
  that boots but cannot play audio is marked good. Decide which checks must pass before `mark-good`.
- [ ] **A hung synchronized unit still delays the main unit.** Since the read timeout, `DefaultPlayerService.play()`
  waits up to 30 seconds (before: 60) for a unit which doesn't answer, while holding the player lock, so stop and
  pause wait as well. Decide how long a play may wait for the other units. Scenario 6.4 "Freeze unit B" confirms it.
- [ ] **API keys have nearly the rights of the admin.** A unit's API key (`ROLE_DEVICE`) can download a backup
  including the plain API keys of its remote units, restore a backup, enable SSH and delete files. Decide what other
  units really need (`ApiSecurityTest` documents the current rules).
- [ ] **Renaming a composition removes it from all sets and from its designer project.** The web app renames by
  deleting the old composition and saving a new one, and the sets and designer projects refer to compositions by name.
  Needs a rename in the backend that updates the sets and projects.
- [ ] **"Previous" during a composition rewinds and selects the previous one.** The code says "rewind instead of
  selecting the previous one", but it does both. Decide which one a press should do (`PlayerSetTest` only covers a
  stopped composition).
- [ ] **Pixel positions differ from the Open Fixture Library.** The app and the designer read `pixelKeys` as
  `[x][y][z]` and count from 0, the library defines `[z][y][x]` and counts from 1. Pixel groups by position (e.g. odd
  pixels, a row) of library fixtures select other pixels than the library means. Both projects need the same change.
- [ ] **DMX of a universe mapping that no longer exists goes nowhere.** Even with a single universe left, which
  otherwise gets everything.
- [ ] **Compositions without files don't continue in a set.** A composition without audio, video or MIDI files (e.g.
  only lighting) doesn't select or start the next composition of the set at its end.
- [ ] **The master dimmer of the designer only dims the preview.** The DMX values sent by the app ignore it.
- [ ] **Pixel group channels of the fixture library are not supported** by the designer nor the app: 176 modes of
  the library stay dark on their pixels. Found by playing every mode of the whole library once.
- [ ] **Brightness given by a channel with several capabilities is often not set.** The app and the designer only set
  it when its range goes from "dark" or "off" to "bright" (`DesignerDmxTest`). 36 channels of the library give it
  differently (in percent, only "bright", or not at all), and 6 modes stay dark because of it.
- [ ] **Split colors of a wheel are sent as the slot before them.** The library gives colors between two slots as
  e.g. slot 1.5, the app cuts off the fraction.
- [ ] **MIDI timecode is sent 2 frames ahead.** The app adds the 2 frames the 8 quarter frames take, which receivers
  usually add themselves. Check with a receiver on the rig before changing it.
- [ ] **The fixture library is downloaded at every start.** About 1.5 MB and 600 files rewritten on each boot, which
  wears the SD card, and every start waits for it. Once a week or on demand would do.
- [ ] **Restoring a backup over a full unit needs room for both.** The data is replaced only when the restored backup
  is complete, so the old and the new data are on the unit at once. A new unit only needs room for the backup.
- [ ] **A Compute Module 3 isn't recognized as a Raspberry Pi 3**, so it doesn't get the video pipeline of the Pi 3.
  Only matters if such units exist.
