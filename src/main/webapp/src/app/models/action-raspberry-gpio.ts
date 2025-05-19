import { Action } from "./action";

export class ActionRaspberryGpio extends Action {
  pinId: number;
  high: boolean;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.pinId = data.pinId;
    this.high = data.high;

    if(!this.pinId) {
      this.pinId = 4;
    }
  }

  toJSON() {
    return { actionRaspberryGpio: { ...this } };
  }
}
