import { Action } from "./action";

// Light the stage with the scenes of a designer project, like the Play screen does by hand
export class ActionScene extends Action {
  sceneActionType: string;
  projectName: string;

  // the scene to play, by its uuid (or by its name)
  scene: string;

  // the folder NEXT_SCENE and PREVIOUS_SCENE step through, by its uuid (or by its name; none = all scenes)
  folder: string;

  // the scene plays until it is stopped: by another scene or by turning all scenes off
  untilStopped: boolean = true;

  // how long the scene plays otherwise, before it is turned off again (0 = until stopped as well)
  durationMillis: number = 0;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.sceneActionType = data.sceneActionType;
    this.projectName = data.projectName;
    this.scene = data.scene;
    this.folder = data.folder;
    this.durationMillis = data.durationMillis || 0;

    // a scene played without a duration plays until stopped too
    this.untilStopped = data.untilStopped || !this.durationMillis;

    if (!this.sceneActionType) {
      this.sceneActionType = "PLAY_SCENE";
    }
  }

  toJSON() {
    return { actionScene: { ...this } };
  }
}
