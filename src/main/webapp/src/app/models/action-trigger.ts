import { Action } from "./action";
import { ActionHttp } from "./action-http";
import { ActionLighting } from "./action-lighting";
import { ActionMidi } from "./action-midi";
import { ActionNull } from "./action-null";
import { ActionRaspberryGpio } from "./action-raspberry-gpio";
import { ActionSystem } from "./action-system";
import { ActionTransport } from "./action-transport";

export class ActionTrigger {
  actionList: Action[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    if (data.actionList) {
      for (let action of data.actionList) {
        let actionObject: Action;

        if (action.actionNull) {
          actionObject = new ActionNull(action.nullAction);
        } else if (action.actionSystem) {
          actionObject = new ActionSystem(action.actionSystem);
        } else if (action.actionTransport) {
          actionObject = new ActionTransport(action.actionTransport);
        } else if (action.actionMidi) {
          actionObject = new ActionMidi(action.actionMidi);
        } else if (action.actionLighting) {
          actionObject = new ActionLighting(action.actionLighting);
        } else if (action.actionRaspberryGpio) {
          actionObject = new ActionRaspberryGpio(action.actionRaspberryGpio);
        } else if (action.actionHttp) {
          actionObject = new ActionHttp(action.actionHttp);
        } else {
          console.warn("Uknown action type", action);
        }

        this.actionList.push(actionObject);
      }
    }
  }
}
