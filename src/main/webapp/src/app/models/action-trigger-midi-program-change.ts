import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiProgramChange extends ActionTriggerMidi {
  program: number = 0;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.program = data.program;
  }

  toJSON() {
    return { actionTriggerMidiProgramChange: { ...this } };
  }
}
