import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiSongSelect extends ActionTriggerMidi {
  song: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.song = data.song;

    if (!this.song) {
      this.song = 0;
    }
  }

  toJSON() {
    return { actionTriggerMidiSongSelect: { ...this } };
  }
}
