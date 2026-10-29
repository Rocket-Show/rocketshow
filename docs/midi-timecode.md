# MIDI Timecode (MTC)

Rocket Show can either **send** MIDI timecode, so other devices follow it, or **follow** a MIDI
timecode it receives, so it plays in sync with an external master such as a DAW or a playback rig.

The mode is set under **Settings → MIDI → MIDI timecode**:

| Mode | What it does |
| --- | --- |
| Off | No timecode is sent or received. |
| Send (master) | Sends quarter-frame messages at the configured frame rate during playback, plus a full-frame message whenever playback starts or is located. |
| Follow (slave) | Follows the timecode received on the configured MIDI input device. |

Sending and following are mutually exclusive — a device can only be one of the two.

To let a master select and start compositions rather than run their timeline, see
[Controlling Rocket Show over MIDI](midi-control.md).

## Sending a timecode

Set **MIDI timecode** to *Send (master)* and choose the frame rate. The timecode is sent on the
configured MIDI output device while a composition plays, and a full-frame message is sent whenever
playback starts or is located.

> Earlier versions sent the quarter-frame messages two frames ahead of the actual position, which
> made every receiver run about 67ms (at 30fps) early. That is fixed, so if you compensated for it
> on the receiving side, remove that compensation.

## Following a timecode

Set **MIDI timecode** to *Follow (slave)* and make sure the MIDI input device carrying the timecode
is selected under **Settings → MIDI → MIDI input**. The frame rate does not have to be configured:
it is taken from the incoming timecode, including 29.97 drop-frame.

While the timecode is running, the master is in charge of the transport: compositions start, stop
and locate with it, and the transport controls in the web app only take effect until the next
timecode update. When the timecode stops, playback pauses and waits where the master parked it.

### Timecode mapping

MIDI timecode carries an absolute position (hh:mm:ss:ff), not "position in composition", so the
incoming position has to be mapped onto something. **Settings → MIDI → Timecode mapping** offers
two ways to do that:

**Per composition** — the incoming timecode drives the composition that is currently selected. The
composition's position is the incoming timecode minus its **Timecode start**, which is configured on
the composition itself in the editor. Use this when you drive one composition at a time and select
the next one yourself (or with an action).

**Per set** — the whole current set shares one continuous timeline. Each composition in the set gets
its own **Timecode start** in the set editor, and occupies the window from there until its duration
runs out. Whichever composition the incoming timecode falls into is selected and started
automatically, so a whole show can be driven by a single continuous timecode. New compositions added
to a set default to starting right after the previous one, which you can then adjust.

If the incoming timecode does not fall into any composition, playback pauses until it does.

### Timecode offset

The timecode reaches Rocket Show a little after the master sent it (MIDI transport) and the audio
leaves the sound card a little after Rocket Show played it (output buffer). **Timecode offset**
compensates both: a positive value plays later, a negative value plays earlier. Measure it once for
your setup and leave it.

## How playback is kept in sync

Lighting, MIDI and action triggers are recalculated from the position on every cycle, so they follow
the master exactly and can jump anywhere without artefacts.

Audio and video cannot be told a position: they are clocked by the sound card and by the display.
They are pulled towards the master in three bands instead:

- below about 10ms nothing is done;
- up to about 100ms the playback rate is trimmed by up to 0.2%, which is inaudible and invisible and
  eases the error out over a few seconds — this is what absorbs the continuous drift between the
  sound card's clock and the master's;
- anything larger is treated as a wrong position rather than drift (the master looped or located, or
  the composition was started late) and is corrected by seeking, which is briefly audible or visible.

Rate trimming needs GStreamer 1.18 or newer. On older versions the drift is simply corrected by
seeking once it grows past the threshold.

## Limitations

- **The master owns the transport.** Looping a composition, automatically starting the next
  composition and sending timecode are not used while following a timecode.
- **Locking takes a moment.** A complete position takes two frames to arrive, and loading and
  prerolling a composition takes longer still. Give the master a few seconds of pre-roll before the
  first cue, or start it from a point where a small correction is not noticeable.
- **Video is frame-accurate at best.** It cannot be rendered at an arbitrary rate, so it follows the
  timecode to within a frame, not to the millisecond.
- **Hardware H.265 video cannot be corrected.** The hardware decoder on the Raspberry Pi can neither
  seek nor pause, so such a composition can only be started in sync and never pulled back afterwards
  (and it keeps playing to its end when the timecode stops). Use H.264 for timecode-driven video.
- **Large corrections are audible.** A resync seek is a jump in the audio and video, not a
  time-stretch. It should be rare in practice; if it is not, check the timecode offset and whether
  the master is sending a stable timecode.
