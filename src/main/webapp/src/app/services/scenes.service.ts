import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { DesignerScene, DesignerSceneFolder, SceneProject } from '../models/scene-project';
import { State } from '../models/state';

/**
 * The scenes of the designer projects, lit by hand on the Play screen. The device plays them and tells every app
 * which ones are on with its state. What is shown on this screen (the project, its folder, stacking scenes or not) is
 * kept here, so it stays the same while navigating.
 */
@Injectable()
export class ScenesService {

  // the icon of a scene without one of its own, like in the designer
  static readonly defaultIcon = 'fa-picture-o';

  projects: SceneProject[] = [];

  // a tap adds a scene to the ones already on, instead of lighting only that scene
  stack: boolean = false;

  // the project and its folder shown (undefined = the project of the scenes on, all of its scenes)
  viewedProjectName: string;
  viewedFolderUuid: string;

  constructor(private http: HttpClient) { }

  loadProjects(): Observable<SceneProject[]> {
    return this.http.get('scenes/projects').pipe(map((result: any[]) => {
      this.projects = result.map((project) => new SceneProject(project));
      return this.projects;
    }));
  }

  // the project shown: the one picked here, the one of the scenes on or the first one
  viewedProject(state: State): SceneProject {
    return this.projects.find((project) => project.name === this.viewedProjectName)
      || this.projects.find((project) => project.name === state?.sceneProjectName)
      || this.projects[0];
  }

  viewProject(project: SceneProject) {
    this.viewedProjectName = project.name;
    this.viewedFolderUuid = undefined;
  }

  // the folders at the top of the scene tree with scenes in them, to pick the scenes shown from
  topFolders(project: SceneProject): DesignerSceneFolder[] {
    if (!project) {
      return [];
    }

    return this.children(project, undefined)
      .filter((child) => child.folder && this.scenesIn(project, child.folder.uuid).length > 0)
      .map((child) => child.folder);
  }

  // the scenes of a folder and all folders inside it (undefined = all scenes), in the order the designer shows them
  scenesIn(project: SceneProject, folderUuid: string): DesignerScene[] {
    if (!project) {
      return [];
    }

    const scenes: DesignerScene[] = [];

    for (const child of this.children(project, folderUuid)) {
      if (child.folder) {
        scenes.push(...this.scenesIn(project, child.folder.uuid));
      } else {
        scenes.push(child.scene);
      }
    }

    return scenes;
  }

  // the folders and scenes of one parent, ordered together like in the scene tree of the designer
  private children(project: SceneProject, parentUuid: string): { folder?: DesignerSceneFolder, scene?: DesignerScene, sortIndex: number }[] {
    const children = [
      ...project.sceneFolders
        .filter((folder) => (folder.parentUuid || undefined) === parentUuid)
        .map((folder) => ({ folder, sortIndex: folder.sortIndex })),
      ...project.scenes
        .filter((scene) => (scene.folderUuid || undefined) === parentUuid)
        .map((scene) => ({ scene, sortIndex: scene.sortIndex })),
    ];

    return children.sort((child1, child2) => child1.sortIndex - child2.sortIndex);
  }

  isOn(scene: DesignerScene, project: SceneProject, state: State): boolean {
    return !!state && state.sceneProjectName === project?.name && state.activeSceneUuids.indexOf(scene.uuid) >= 0;
  }

  // the scenes on, in the order the designer shows them
  scenesOn(state: State): DesignerScene[] {
    const project = this.projects.find((candidate) => candidate.name === state?.sceneProjectName);

    return this.scenesIn(project, undefined).filter((scene) => this.isOn(scene, project, state));
  }

  // A tap lights only this scene, or adds it to the ones on while stacking. A tap on a scene on turns it off.
  tap(scene: DesignerScene, project: SceneProject, state: State): Observable<Object> {
    const on = this.isOn(scene, project, state);
    const sameProject = state.sceneProjectName === project.name;
    let sceneUuids: string[];

    if (this.stack) {
      const current = sameProject ? state.activeSceneUuids : [];
      sceneUuids = on ? current.filter((uuid) => uuid !== scene.uuid) : current.concat([scene.uuid]);
    } else {
      sceneUuids = on && state.activeSceneUuids.length === 1 ? [] : [scene.uuid];
    }

    return this.setActiveScenes(project.name, sceneUuids, state);
  }

  releaseAll(state: State): Observable<Object> {
    return this.setActiveScenes(state.sceneProjectName, [], state);
  }

  // Shown right away, the state of the device confirms it a moment later
  private setActiveScenes(projectName: string, sceneUuids: string[], state: State): Observable<Object> {
    state.sceneProjectName = projectName;
    state.activeSceneUuids = sceneUuids;

    return this.http.post('scenes/active?projectName=' + encodeURIComponent(projectName || ''), sceneUuids);
  }

  setBlackout(blackout: boolean, state: State): Observable<Object> {
    state.sceneBlackout = blackout;

    return this.http.post('scenes/blackout?value=' + blackout, undefined);
  }

  // from 0 to 1
  setMasterDimmer(masterDimmer: number): Observable<Object> {
    return this.http.post('scenes/master-dimmer?value=' + masterDimmer, undefined);
  }

  icon(scene: DesignerScene): string {
    return scene.icon || ScenesService.defaultIcon;
  }

  // The color the designer shows the scene with. Undefined for a scene whose presets decide its color, while the
  // project was saved by a designer which didn't write it yet.
  color(scene: DesignerScene): string {
    return scene.displayColor || (scene.colorAuto ? undefined : scene.color);
  }

}
