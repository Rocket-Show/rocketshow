import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { DesignerScene, DesignerSceneFolder, SceneProject } from '../models/scene-project';
import { SceneMidiMapping, State } from '../models/state';
import { SessionService } from './session.service';

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

  // a scene held flashes it on top of the others, instead of a tap turning it on
  flash: boolean = false;

  // a tap on a scene waits for the key, pad or button of a MIDI controller which plays it from then on (in the way the
  // flash says), and the keys, pads and buttons learned for the scenes of the project shown
  learn: boolean = false;
  midiMappings: SceneMidiMapping[] = [];

  // the project and its folder shown (undefined = the project of the scenes on, all of its scenes)
  viewedProjectName: string;
  viewedFolderUuid: string;

  // what the Play screen shows: the compositions of the set or the scenes. Switched on the transport, or next to the
  // menu on a small screen.
  playMode: 'compositions' | 'scenes' = 'compositions';

  constructor(private http: HttpClient, private sessionService: SessionService) { }

  loadProjects(): Observable<SceneProject[]> {
    return this.http.get('scenes/projects').pipe(map((result: any[]) => {
      this.projects = result.map((project) => new SceneProject(project));
      return this.projects;
    }));
  }

  // the scenes can only be shown with a designer project with scenes
  scenesAvailable(): boolean {
    return this.projects.length > 0;
  }

  showScenes(): boolean {
    return this.playMode === 'scenes' && this.scenesAvailable();
  }

  setPlayMode(playMode: 'compositions' | 'scenes') {
    this.playMode = playMode;
    this.sessionService.setPlayMode(playMode).subscribe();

    // the scenes may have been changed in the designer meanwhile
    if (playMode === 'scenes') {
      this.loadProjects().subscribe();
    }
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

  // the scenes flashed at the moment
  isFlashed(scene: DesignerScene, project: SceneProject, state: State): boolean {
    return !!state && state.sceneProjectName === project?.name && state.flashedSceneUuids.indexOf(scene.uuid) >= 0;
  }

  // Light a scene on top of the others at once while it is held (on), and turn it off again
  flashScene(scene: DesignerScene, project: SceneProject, on: boolean, state: State): Observable<Object> {
    if (state) {
      const others = state.sceneProjectName === project.name ? state.flashedSceneUuids.filter((uuid) => uuid !== scene.uuid) : [];
      state.flashedSceneUuids = on ? others.concat([scene.uuid]) : others;
    }

    return this.http.post(this.url('scenes/flash', { projectName: project.name, scene: scene.uuid, on: '' + on }), null);
  }

  // the folder shown, if it is one of the project shown (undefined = all its scenes)
  viewedFolder(project: SceneProject): string {
    return this.topFolders(project).some((folder) => folder.uuid === this.viewedFolderUuid) ? this.viewedFolderUuid : undefined;
  }

  // Play the next (1) or the previous (-1) scene of the folder shown instead of the ones on
  step(project: SceneProject, direction: number): Observable<Object> {
    const parameters: { [name: string]: string } = { projectName: project.name };
    const folder = this.viewedFolder(project);

    if (folder) {
      parameters.folder = folder;
    }

    return this.http.post(this.url(direction < 0 ? 'scenes/previous' : 'scenes/next', parameters), null);
  }

  // the speed master, 1 = as written
  setSpeed(speed: number): Observable<Object> {
    return this.http.post(this.url('scenes/speed', { value: '' + speed }), null);
  }

  // the dimmer of a scene on the Play screen, from 0 to 1
  setSceneDimmer(scene: DesignerScene, dimmer: number, state: State): Observable<Object> {
    if (state) {
      state.sceneDimmers = { ...state.sceneDimmers, [scene.uuid]: dimmer };
    }

    return this.http.post(this.url('scenes/dimmer', { scene: scene.uuid, value: '' + dimmer }), null);
  }

  // Wait for the key, pad or button of a MIDI controller which plays the scene from now on, in the way the flash says
  learnScene(scene: DesignerScene, project: SceneProject, state: State): Observable<Object> {
    const mode = this.flash ? 'flash' : 'toggle';

    if (state) {
      state.sceneLearning = { projectName: project.name, scene: scene.uuid, mode };
    }

    return this.http.post(this.url('scenes/learn', { projectName: project.name, scene: scene.uuid, mode }), null);
  }

  cancelLearn(state: State): Observable<Object> {
    if (state) {
      state.sceneLearning = undefined;
    }

    return this.http.post('scenes/learn-cancel', null);
  }

  loadMidiMappings(project: SceneProject): Observable<SceneMidiMapping[]> {
    return this.http.get(this.url('scenes/midi', { projectName: project?.name || '' })).pipe(
      map((mappings: SceneMidiMapping[]) => {
        this.midiMappings = mappings || [];
        return this.midiMappings;
      })
    );
  }

  // the keys, pads and buttons learned for a scene, in the way the flash says
  midiMappingsOf(scene: DesignerScene): SceneMidiMapping[] {
    const mode = this.flash ? 'flash' : 'toggle';
    return this.midiMappings.filter((mapping) => mapping.scene === scene.uuid && mapping.mode === mode);
  }

  forgetMidi(scene: DesignerScene, project: SceneProject): Observable<Object> {
    const mode = this.flash ? 'flash' : 'toggle';
    this.midiMappings = this.midiMappings.filter((mapping) => mapping.scene !== scene.uuid || mapping.mode !== mode);

    return this.http.post(this.url('scenes/midi-forget', { projectName: project.name, scene: scene.uuid, mode }), null);
  }

  // an address with its parameters, each of them encoded
  private url(path: string, parameters: { [name: string]: string }): string {
    return path + '?' + Object.keys(parameters).map((name) => name + '=' + encodeURIComponent(parameters[name])).join('&');
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
