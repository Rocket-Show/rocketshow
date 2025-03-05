import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiNoteOn extends ActionTriggerMidi {
  note: number = 0;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.note = data.note;
  }

  toJSON() {
    return { actionTriggerMidiNoteOn: { ...this } };
  }
}
