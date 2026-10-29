import { Action } from "./action";

// Light the stage with the scenes of a designer project, like the Play screen does by hand
export class ActionScene extends Action {
  sceneActionType: string;
  projectName: string;

  // the scene to play, by its uuid (or by its name)
  scene: string;

  // how long the scene plays, before it is turned off again (0 = until other scenes are picked)
  durationMillis: number = 0;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.sceneActionType = data.sceneActionType;
    this.projectName = data.projectName;
    this.scene = data.scene;
    this.durationMillis = data.durationMillis || 0;

    if (!this.sceneActionType) {
      this.sceneActionType = "PLAY_SCENE";
    }
  }

  toJSON() {
    return { actionScene: { ...this } };
  }
}
