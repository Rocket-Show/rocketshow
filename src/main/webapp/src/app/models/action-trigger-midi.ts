import { ActionTrigger } from "./action-trigger";

export class ActionTriggerMidi extends ActionTrigger {
  channel: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.channel = data.channel;
  }
}
