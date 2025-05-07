import { Action } from "./action";

export class ActionSystem extends Action {
  systemActionType: string;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.systemActionType = data.systemActionType;

    if (!this.systemActionType) {
      this.systemActionType = "REBOOT";
    }
  }

  toJSON() {
    return { actionSystem: { ...this } };
  }
}
