import { Action } from "./action";
import { ActionHttp } from "./action-http";
import { ActionLighting } from "./action-lighting";
import { ActionMidi } from "./action-midi";
import { ActionNull } from "./action-null";
import { ActionRaspberryGpio } from "./action-raspberry-gpio";
import { ActionSystem } from "./action-system";
import { ActionTransport } from "./action-transport";
import { ActionTriggerComposition } from "./action-trigger-composition";

/**
 * The actions of a trigger, saved and loaded again: each one keeps its type.
 */
describe("Actions of a trigger", () => {
  const actionClasses: { [type: string]: new (data?: any) => Action } = {
    NULL: ActionNull,
    SYSTEM: ActionSystem,
    TRANSPORT: ActionTransport,
    MIDI: ActionMidi,
    LIGHTING: ActionLighting,
    RASPBERRY_GPIO: ActionRaspberryGpio,
    HTTP: ActionHttp,
  };

  // Like the action editor: a new action has no type, until one is selected
  function newAction(type: string): Action {
    const action = new ActionNull();
    action.type = "NULL";

    if (type == "NULL") {
      return action;
    }

    action.type = type;
    return new actionClasses[type](action);
  }

  function saveAndLoad(trigger: ActionTriggerComposition): ActionTriggerComposition {
    return new ActionTriggerComposition(JSON.parse(JSON.stringify(trigger)));
  }

  for (const type of Object.keys(actionClasses)) {
    it("keeps the type of a " + type + " action", () => {
      const trigger = new ActionTriggerComposition();
      const action = newAction(type);
      action.executeLocally = false;
      trigger.actionList.push(action);

      const loaded = saveAndLoad(trigger).actionList[0];

      expect(loaded).toBeInstanceOf(actionClasses[type]);
      expect(loaded.type).toBe(type);
      expect(loaded.executeLocally).toBeFalse();
    });
  }

  it("loads a NULL action as the unit sends it", () => {
    const trigger = new ActionTriggerComposition({
      positionMillis: 1000,
      actionList: [{ actionNull: { remoteDeviceNames: [], executeLocally: true, type: "NULL" } }],
    });

    expect(trigger.actionList[0]).toBeInstanceOf(ActionNull);
    expect(trigger.actionList[0].type).toBe("NULL");
  });
});
