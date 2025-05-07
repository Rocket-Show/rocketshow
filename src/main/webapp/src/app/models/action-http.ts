import { Action } from "./action";

export class ActionHttp extends Action {
  systemActionType: string = 'REBOOT';

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.systemActionType = data.systemActionType;
  }

  toJSON() {
    return { actionHttp: { ...this } };
  }
}
