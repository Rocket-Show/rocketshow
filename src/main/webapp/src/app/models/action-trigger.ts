import { Action } from "./action";
import { ActionNull } from "./action-null";

export class ActionTrigger {
  actionList: Action[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    if (data.actionList) {
      for (let action of data.actionList) {
        let actionObject: Action;

        if (data.nullAction) {
          actionObject = new ActionNull(data.nullAction);
        } else if (data.xxx) {
          // TODO
        }

        this.actionList.push(actionObject);
      }
    }
  }
}
