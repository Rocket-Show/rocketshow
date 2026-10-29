import { ActionTriggerMidi } from "./action-trigger-midi";

export class ActionTriggerMidiSystemRealTime extends ActionTriggerMidi {
  systemRealTimeType: string = "START";

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.systemRealTimeType = data.systemRealTimeType;

    if (!this.systemRealTimeType) {
      this.systemRealTimeType = "START";
    }
  }

  toJSON() {
    return { actionTriggerMidiSystemRealTime: { ...this } };
  }
}
