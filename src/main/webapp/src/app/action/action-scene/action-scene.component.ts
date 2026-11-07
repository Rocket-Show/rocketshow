import { Component, Input, OnInit } from "@angular/core";
import { ActionScene } from "../../models/action-scene";
import { DesignerScene, SceneProject } from "../../models/scene-project";
import { ScenesService } from "../../services/scenes.service";

@Component({
    selector: "app-action-scene",
    templateUrl: "./action-scene.component.html",
    styleUrl: "./action-scene.component.scss",
    standalone: false
})
export class ActionSceneComponent implements OnInit {
  @Input()
  action: ActionScene;

  public typeList: string[] = [
    "PLAY_SCENE",
    "TOGGLE_SCENE",
    "FLASH_SCENE",
    "NEXT_SCENE",
    "PREVIOUS_SCENE",
    "RELEASE_ALL",
    "BLACKOUT_ON",
    "BLACKOUT_OFF",
    "TOGGLE_BLACKOUT",
    "TAP_TEMPO",
  ];

  constructor(public scenesService: ScenesService) {}

  ngOnInit() {
    this.scenesService.loadProjects().subscribe(() => {
      // a new action plays a scene of the first project, until another one is picked
      if (!this.action.projectName && this.scenesService.projects.length > 0) {
        this.selectProject(this.scenesService.projects[0].name);
      }
    });
  }

  project(): SceneProject {
    return this.scenesService.projects.find((project) => project.name === this.action.projectName);
  }

  // the scenes of the project, in the order the designer shows them
  scenes(): DesignerScene[] {
    return this.scenesService.scenesIn(this.project(), undefined);
  }

  selectProject(projectName: string) {
    this.action.projectName = projectName;

    // the scene and the folder picked so far belong to the project picked before
    this.action.scene = this.scenes()[0]?.uuid;
    this.action.folder = undefined;
  }

  // the types playing one scene, which pick a project and a scene
  playsScene(): boolean {
    return ["PLAY_SCENE", "TOGGLE_SCENE", "FLASH_SCENE"].indexOf(this.action.sceneActionType) >= 0;
  }

  // the types stepping through the scenes of a folder
  stepsScenes(): boolean {
    return ["NEXT_SCENE", "PREVIOUS_SCENE"].indexOf(this.action.sceneActionType) >= 0;
  }

  // the folders of the project at the top of its scene tree, to step through
  folders() {
    return this.scenesService.topFolders(this.project());
  }

  setUntilStopped(untilStopped: boolean) {
    this.action.untilStopped = untilStopped;

    if (!untilStopped && !this.action.durationMillis) {
      this.action.durationMillis = 10000;
    }
  }

  durationSeconds(): number {
    return (this.action.durationMillis || 0) / 1000;
  }

  setDurationSeconds(seconds: number) {
    this.action.durationMillis = Math.max(0, Math.round((seconds || 0) * 1000));
  }
}
