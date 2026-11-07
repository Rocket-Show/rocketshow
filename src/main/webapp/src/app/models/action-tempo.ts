import { Action } from "./action";

// The tempo the beat-synced effects run at, from outside: a beat tapped, the first beat of a bar, a tempo set, halved,
// doubled, a little faster or slower, or back to the tempo of the composition or project playing
export class ActionTempo extends Action {
  tempoActionType: string;

  // the tempo set (SET)
  beatsPerMinute: number = 120;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.tempoActionType = data.tempoActionType;
    this.beatsPerMinute = data.beatsPerMinute > 0 ? data.beatsPerMinute : 120;

    if (!this.tempoActionType) {
      this.tempoActionType = "TAP";
    }
  }

  toJSON() {
    return { actionTempo: { ...this } };
  }
}
