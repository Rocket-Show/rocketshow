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

  public typeList: string[] = ["PLAY_SCENE", "RELEASE_ALL", "BLACKOUT_ON", "BLACKOUT_OFF", "TOGGLE_BLACKOUT"];

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

    // the scene picked so far belongs to the project picked before
    this.action.scene = this.scenes()[0]?.uuid;
  }

  durationSeconds(): number {
    return (this.action.durationMillis || 0) / 1000;
  }

  setDurationSeconds(seconds: number) {
    this.action.durationMillis = Math.max(0, Math.round((seconds || 0) * 1000));
  }
}
