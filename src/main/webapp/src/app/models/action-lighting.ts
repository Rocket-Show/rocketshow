import { Action } from "./action";

export class ActionLighting extends Action {
  systemActionType: string = 'REBOOT';

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.systemActionType = data.systemActionType;
  }

  toJSON() {
    return { actionLighting: { ...this } };
  }
}
