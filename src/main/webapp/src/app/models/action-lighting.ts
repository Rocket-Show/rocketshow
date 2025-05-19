import { Action } from "./action";
import { LightingActionUniverse } from "./lighting-action-universe";

export class ActionLighting extends Action {
  lightingActionUniverseList: LightingActionUniverse[] = [];

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    if (data.lightingActionUniverseList) {
      for (let lightingActionUniverse of data.lightingActionUniverseList) {
        this.lightingActionUniverseList.push(
          new LightingActionUniverse(lightingActionUniverse)
        );
      }
    }

    if (this.lightingActionUniverseList.length === 0) {
      this.lightingActionUniverseList.push(new LightingActionUniverse());
    }
  }

  toJSON() {
    return { actionLighting: { ...this } };
  }
}
