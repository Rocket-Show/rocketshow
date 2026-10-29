import { Action } from "./action";
import { ActionHttp } from "./action-http";
import { ActionLighting } from "./action-lighting";
import { ActionMidi } from "./action-midi";
import { ActionNull } from "./action-null";
import { ActionRaspberryGpio } from "./action-raspberry-gpio";
import { ActionScene } from "./action-scene";
import { ActionSystem } from "./action-system";
import { ActionTransport } from "./action-transport";
import { ActionTriggerComposition } from "./action-trigger-composition";

/**
 * The actions of a trigger, saved and loaded again: each one keeps its type and the other units executing it.
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
    SCENE: ActionScene,
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
      action.remoteDeviceNames.push("Unit B");
      trigger.actionList.push(action);

      const loaded = saveAndLoad(trigger).actionList[0];

      expect(loaded).toBeInstanceOf(actionClasses[type]);
      expect(loaded.type).toBe(type);
      expect(loaded.executeLocally).toBeFalse();
      expect(loaded.remoteDeviceNames).toEqual(["Unit B"]);
    });
  }

  it("loads a NULL action as the unit sends it", () => {
    const trigger = new ActionTriggerComposition({
      positionMillis: 1000,
      actionList: [{ actionNull: { remoteDeviceNames: ["Unit B"], executeLocally: true, type: "NULL" } }],
    });

    expect(trigger.actionList[0]).toBeInstanceOf(ActionNull);
    expect(trigger.actionList[0].type).toBe("NULL");
    expect(trigger.actionList[0].remoteDeviceNames).toEqual(["Unit B"]);
  });

  it("keeps the scene a SCENE action plays and for how long", () => {
    const trigger = new ActionTriggerComposition();
    const action = newAction("SCENE") as ActionScene;
    action.projectName = "Club Lights";
    action.scene = "warm-wash";
    action.untilStopped = false;
    action.durationMillis = 5000;
    trigger.actionList.push(action);

    const loaded = saveAndLoad(trigger).actionList[0] as ActionScene;

    expect(loaded.sceneActionType).toBe("PLAY_SCENE");
    expect(loaded.projectName).toBe("Club Lights");
    expect(loaded.scene).toBe("warm-wash");
    expect(loaded.untilStopped).toBeFalse();
    expect(loaded.durationMillis).toBe(5000);
  });

  it("plays the scene of a new SCENE action until it is stopped", () => {
    const trigger = new ActionTriggerComposition();
    trigger.actionList.push(newAction("SCENE"));

    expect((saveAndLoad(trigger).actionList[0] as ActionScene).untilStopped).toBeTrue();
  });

  // As the unit plays it
  it("plays the scene of a SCENE action without a duration until it is stopped", () => {
    const trigger = new ActionTriggerComposition({
      actionList: [{ actionScene: { type: "SCENE", sceneActionType: "PLAY_SCENE", untilStopped: false, durationMillis: 0 } }],
    });

    expect((trigger.actionList[0] as ActionScene).untilStopped).toBeTrue();
  });

  // The unit ignores fields it doesn't know
  it("sends the other units executing an action under the name the unit reads", () => {
    const action = newAction("TRANSPORT");
    action.remoteDeviceNames.push("Unit B");

    const saved = JSON.parse(JSON.stringify(action)).actionTransport;

    expect(saved.remoteDeviceNames).toEqual(["Unit B"]);
    expect(saved.remoteDeviceList).toBeUndefined();
  });
});
