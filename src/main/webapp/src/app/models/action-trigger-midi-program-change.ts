import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiProgramChange extends ActionTriggerMidi {
  program: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.program = data.program;

    if(!this.program) {
      this.program = 0;
    }
  }

  toJSON() {
    return { actionTriggerMidiProgramChange: { ...this } };
  }
}
