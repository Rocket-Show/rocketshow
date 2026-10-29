import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiControlChange extends ActionTriggerMidi {
  controller: number;
  value: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.controller = data.controller;
    this.value = data.value;

    if (!this.controller) {
      this.controller = 0;
    }
  }

  toJSON() {
    return { actionTriggerMidiControlChange: { ...this } };
  }
}
