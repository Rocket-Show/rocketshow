import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiNoteOn extends ActionTriggerMidi {
  note: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.note = data.note;

    if(!this.note) {
      this.note = 0;
    }
  }

  toJSON() {
    return { actionTriggerMidiNoteOn: { ...this } };
  }
}
