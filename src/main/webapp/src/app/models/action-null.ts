import { Action } from "./action";

export class ActionNull extends Action {
  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    // Empty
  }

  toJSON() {
    return { actionNull: { ...this } };
  }
}
