# Controlling Rocket Show over MIDI

A master — a DAW, a lighting desk, a show control system, a footswitch — can tell Rocket Show which
composition to open and when to play it. There are several standards for this, and Rocket Show
speaks all of the common ones. Which one to use depends on what is sending.

| You are driving from | Use |
| --- | --- |
| A lighting desk or show control system (ETC, grandMA, QLab, Medialon, Chamsys) | MIDI Show Control |
| A DAW | Program change or song select to select, MIDI Machine Control or the transport messages to play |
| A keyboard, footswitch or simple controller | Program change, or a control mapped under Control |
| Another Rocket Show or anything sending timecode | [MIDI Timecode](midi-timecode.md) |

All of it arrives on the MIDI input device configured under **Settings → MIDI**.

## Numbering your compositions

Instead of mapping every composition separately, give each one a number and let the master address
it by that number. Two fields on a composition do this, both in the composition editor:

- **MIDI number** — selects the composition with a program change or a song select. Counted from 1,
  so program change 1 selects the composition numbered 1.
- **Show control cue** — the MIDI Show Control cue number, e.g. `12.5`. Leave it empty and the MIDI
  number is used as the cue number instead, so numbering a composition once is usually enough.

Both fields only appear once the matching feature is switched on in the settings.

**A set can renumber its compositions.** In the set editor each composition gets its own MIDI number
and cue number for that set, so every show can count its songs from 1 regardless of how the
compositions are numbered globally. Leave them empty to inherit the composition's own numbers. The
current set is always searched first, and only then all compositions.

## Program change and song select

Switch on **Settings → MIDI → Select compositions by MIDI number**.

- A **program change** selects the composition whose MIDI number matches. Optionally restrict it to
  one channel.
- A preceding **bank select** (CC 0 and CC 32) extends the range past 128: bank 1, program 72 is
  MIDI number 200. Without a bank select the program number alone is used, so nothing changes for
  masters that do not send one.
- A **song select** does the same, using the song number. It carries no channel.

By default a composition is only *selected*, not started, so the master can select it early and
start it exactly on time. Switch on **Start playing immediately** if your master only sends a
program change and expects playback to begin.

To play, stop or pause, map a message to an action under **Settings → MIDI → Control**. Besides
notes and program changes you can now also trigger on a **control change** (any controller, with an
optional exact value — 127 for a pressed footswitch), a **song select**, and the MIDI **transport**
messages Start, Continue and Stop that a master sends to run everything on the line at once.

## Playing the scenes live

The scenes of a designer project can be played by hand on the **Play** screen (switch it to **Scenes**), and
from a MIDI controller:

- **MIDI learn.** Switch on **MIDI learn**, tap a scene and press the key, pad or button which is to play it.
  A note or a control change is taken, on its channel. A key learned toggles the scene; with **Flash**
  switched on, it flashes the scene while it is held instead. A key learned for another scene plays that one
  from then on. The keys learned show on the tiles while MIDI learn is on, each with a button to forget it.
  They are actions like any other under **Settings → MIDI → Control**.
- **Flash.** A flashed scene is on at once while its key is held, on top of the others, and off at once when
  it is let go: on the note off (or a note on with velocity 0), or on the control change to 0 of a button
  sending another value when pressed.
- **Back and GO** play the previous and the next scene of the folder shown, in the order of the designer.
  The actions **Next Scene** and **Previous Scene** do the same from a footswitch, for a folder of your choice.
- **Tempo.** Effects synced to the beat follow the tempo of the project. Tap a tempo with **Tap** (or the
  action **Tap Tempo**): each tap falls on a beat. A **MIDI clock** on the MIDI input sets the tempo as well,
  and once started (MIDI Start) its beats are the beats of the scenes. The **speed master** runs the effects
  and the steps of the scenes faster or slower.

A scene picked fades in over its fade in time and fades out over its fade out time when it is let go, and
plays its presets from the moment it is picked: their steps start with the first one.

## MIDI Show Control

Switch on **Settings → MIDI → MIDI Show Control** and set the **device ID** the desk addresses this
machine with (0–126; commands sent to the all-call ID 127 are always accepted).

| Command | What Rocket Show does |
| --- | --- |
| `LOAD` with a cue number | Selects the composition with that cue number, without playing |
| `GO` with a cue number | Selects that composition and starts it |
| `GO` without a cue number | Starts what is currently selected |
| `STOP` | Pauses |
| `RESUME` | Continues |
| `ALL_OFF`, `RESET`, `GO_OFF` | Stops |

Cue numbers are compared as text, but `1`, `01` and `1.0` are treated as the same cue, which is how
most desks write them. The cue list and cue path a command may carry are ignored — only the cue
number selects a composition. The command format byte (lighting, sound, video, all-types) is not
filtered either: in MSC it is the device ID that says who a command is for.

## MIDI Machine Control

Switch on **Settings → MIDI → MIDI Machine Control** and set its device ID the same way.

MMC only carries transport: `PLAY` and `DEFERRED PLAY` start, `STOP` stops, `PAUSE` pauses, and
`LOCATE` seeks to a position. It does not say which composition to play, so pair it with a program
change or with MSC.

MMC is ignored while this device is following a [MIDI timecode](midi-timecode.md) — the timecode
master already owns the transport, and letting MMC seek as well would only fight it.

## Limitations

- **No standard carries a composition name.** MSC cue numbers are text but the specification limits
  them to digits and dots; everything else is numeric. Whichever protocol you use, the master
  addresses a number and Rocket Show looks up which composition carries it.
- **Selecting takes as long as loading.** A composition is loaded and prerolled when it is selected,
  which takes a moment for large video files. Select it before you need it rather than at the
  downbeat, or let a timecode drive the show instead.
- **MSC group IDs are not supported.** Device IDs 112–126 are group addresses in the specification;
  Rocket Show only matches the exact ID it is configured with, plus the all-call ID 127.
- **`TIMED_GO` is treated as a plain `GO`** — the fade time an MSC command may carry is ignored.
