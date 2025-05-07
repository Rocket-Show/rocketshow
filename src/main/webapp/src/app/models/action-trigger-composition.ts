import { ActionTrigger } from "./action-trigger";

export class ActionTriggerComposition extends ActionTrigger {
  positionMillis: number = 0;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.positionMillis = data.positionMillis;
  }
}
