// Absolute MIDI timecode positions are stored in milliseconds but entered and displayed as
// hh:mm:ss.mmm, which is how a timecode master (a DAW, a playback system) shows them.

function pad(value: number, length: number): string {
  let padded: string = value.toString();

  while (padded.length < length) {
    padded = "0" + padded;
  }

  return padded;
}

export function formatTimecode(millis: number): string {
  const total: number = Math.max(0, Math.round(millis || 0));

  return (
    pad(Math.floor(total / 3600000), 2) +
    ":" +
    pad(Math.floor((total % 3600000) / 60000), 2) +
    ":" +
    pad(Math.floor((total % 60000) / 1000), 2) +
    "." +
    pad(total % 1000, 3)
  );
}

// Accepts hh:mm:ss.mmm, mm:ss.mmm and ss.mmm; anything unparseable becomes 0
export function parseTimecode(value: string): number {
  if (!value) {
    return 0;
  }

  const parts: string[] = value.trim().split(":");

  if (parts.length > 3) {
    return 0;
  }

  let millis: number = 0;
  const multipliers: number[] = [1000, 60000, 3600000];

  for (let index = 0; index < parts.length; index++) {
    const part: number = parseFloat(parts[parts.length - 1 - index].replace(",", "."));

    if (isNaN(part) || part < 0) {
      return 0;
    }

    millis += part * multipliers[index];
  }

  return Math.round(millis);
}
