import { Action } from "./action";

export class ActionTransport extends Action {
  transportActionType: string;
  compositionName: string;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.transportActionType = data.transportActionType;
    this.compositionName = data.compositionName;

    if (!this.transportActionType) {
      this.transportActionType = "PLAY";
    }
  }

  toJSON() {
    return { actionTransport: { ...this } };
  }
}
