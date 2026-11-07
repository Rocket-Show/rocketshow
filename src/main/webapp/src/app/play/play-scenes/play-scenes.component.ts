import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError } from 'rxjs/operators';
import { DesignerScene, DesignerSceneFolder, SceneProject } from '../../models/scene-project';
import { SceneMidiMapping, State } from '../../models/state';
import { ScenesService } from '../../services/scenes.service';
import { ToastGeneralErrorService } from '../../services/toast-general-error.service';
import { TransportService } from '../../services/transport.service';

// A tile of a scene: what it shows and how it is marked
interface SceneTile {
  scene: DesignerScene;
  icon: string;
  on: boolean;
  // on, but a composition (or a blackout) keeps it dark at the moment
  held: boolean;
  color: string;
  // a text color readable on the tile while it is lit with the scene's color
  textColor: string;
  dimmerPercent: number;
  // flashed at the moment, its dimmer on the Play screen, waiting for its key of a MIDI controller and the keys learned
  // for it
  flashed: boolean;
  liveDimmerPercent: number;
  learning: boolean;
  midi: SceneMidiMapping[];
}

// The scenes of a designer project as large tiles, lit by hand with a tap
@Component({
  selector: 'app-play-scenes',
  templateUrl: './play-scenes.component.html',
  styleUrls: ['./play-scenes.component.scss'],
  standalone: false
})
export class PlayScenesComponent implements OnChanges, OnDestroy {

  // what a lit scene without a color of its own is shown with
  private static readonly fallbackColor = '#fd7e14';

  @Input() state: State;

  // the shortest time between two values of a dimmer sent while it is dragged
  private static readonly throttleMillis = 100;

  // the scene flashed by the pointer held on it
  private flashedTile: SceneTile;

  // the dimmers being dragged: the timer sending the last value, and when a value was sent last, by scene
  private dimmerTimeouts = new Map<string, any>();
  private dimmerSentMillis = new Map<string, number>();

  constructor(
    public scenesService: ScenesService,
    private transportService: TransportService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private translateService: TranslateService,
  ) { }

  ngOnChanges(changes: SimpleChanges) {
    // a key learned for a scene: show it with the others
    const previous: State = changes.state?.previousValue;

    if (this.scenesService.learn && previous?.sceneLearning && !this.state?.sceneLearning) {
      this.loadMidiMappings();
    }
  }

  ngOnDestroy() {
    this.dimmerTimeouts.forEach((timeout) => clearTimeout(timeout));
  }

  loadMidiMappings() {
    this.scenesService
      .loadMidiMappings(this.project())
      .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
      .subscribe();
  }

  project(): SceneProject {
    return this.scenesService.viewedProject(this.state);
  }

  folders(): DesignerSceneFolder[] {
    return this.scenesService.topFolders(this.project());
  }

  viewFolder(folder: DesignerSceneFolder) {
    this.scenesService.viewedFolderUuid = folder?.uuid;
  }

  viewProject(project: SceneProject) {
    this.scenesService.viewProject(project);

    if (this.scenesService.learn) {
      this.loadMidiMappings();
    }
  }

  // a composition plays, not just the preview of the designer
  compositionPlaying(): boolean {
    return ['PLAYING', 'PAUSED', 'LOADING', 'LOADED'].indexOf(this.state?.playState) >= 0;
  }

  tiles(): SceneTile[] {
    const project = this.project();
    const folders = this.folders();
    // a folder picked in another project shows all scenes
    const folderUuid = folders.some((folder) => folder.uuid === this.scenesService.viewedFolderUuid) ? this.scenesService.viewedFolderUuid : undefined;

    return this.scenesService.scenesIn(project, folderUuid).map((scene) => {
      const on = this.scenesService.isOn(scene, project, this.state);
      const flashed = this.scenesService.isFlashed(scene, project, this.state);
      const color = this.scenesService.color(scene) || PlayScenesComponent.fallbackColor;
      const learning = this.state?.sceneLearning;

      return {
        scene,
        icon: this.scenesService.icon(scene),
        on,
        held: (on || flashed) && (this.state.sceneBlackout || this.state.scenesHeld),
        color,
        textColor: this.readableOn(color),
        dimmerPercent: Math.round(scene.dimmer * 100),
        flashed,
        liveDimmerPercent: Math.round((this.state?.sceneDimmers?.[scene.uuid] ?? 1) * 100),
        learning: !!learning && learning.projectName === project?.name && learning.scene === scene.uuid,
        midi: this.scenesService.learn ? this.scenesService.midiMappingsOf(scene) : [],
      };
    });
  }

  trackTile(index: number, tile: SceneTile): string {
    return tile.scene.uuid;
  }

  // A tap turns a scene on or off, waits for its key of a MIDI controller while learning, and does nothing while the
  // scenes are flashed (see press)
  tap(tile: SceneTile) {
    if (this.scenesService.learn) {
      this.scenesService
        .learnScene(tile.scene, this.project(), this.state)
        .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
        .subscribe();
      return;
    }

    if (this.scenesService.flash) {
      return;
    }

    this.scenesService
      .tap(tile.scene, this.project(), this.state)
      .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
      .subscribe();
  }

  // While the scenes are flashed, a scene is on while the pointer is held on it
  press(tile: SceneTile, event: PointerEvent) {
    if (!this.scenesService.flash || this.scenesService.learn || this.flashedTile) {
      return;
    }

    // the scene stays on while the pointer is held, also when it leaves the tile
    (event.target as Element)?.setPointerCapture?.(event.pointerId);
    this.flashedTile = tile;
    this.flashScene(tile, true);
  }

  letGo(tile: SceneTile) {
    if (!this.flashedTile || this.flashedTile.scene.uuid !== tile.scene.uuid) {
      return;
    }

    this.flashedTile = undefined;
    this.flashScene(tile, false);
  }

  private flashScene(tile: SceneTile, on: boolean) {
    this.scenesService
      .flashScene(tile.scene, this.project(), on, this.state)
      .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
      .subscribe();
  }

  forget(tile: SceneTile) {
    this.scenesService
      .forgetMidi(tile.scene, this.project())
      .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
      .subscribe();
  }

  // a key, pad or button learned, e.g. "Note 36 · Ch 1"
  midiLabel(mapping: SceneMidiMapping): string {
    const number = this.translateService.instant(mapping.kind === 'cc' ? 'play.scenes-midi-cc' : 'play.scenes-midi-note', { number: mapping.number });
    return mapping.channel === null || mapping.channel === undefined ? number : number + ' · ' + this.translateService.instant('play.scenes-midi-channel', { channel: mapping.channel + 1 });
  }

  // the name of the scene waiting for its key
  learningSceneName(): string {
    const learning = this.state?.sceneLearning;
    const project = this.scenesService.projects.find((candidate) => candidate.name === learning?.projectName);

    return project?.scenes.find((scene) => scene.uuid === learning?.scene)?.name || '';
  }

  // The dimmer of a scene, sent while it is dragged, but not more often than the throttle allows. The last value is
  // always sent.
  setDimmer(tile: SceneTile, percent: number) {
    const uuid = tile.scene.uuid;
    const dimmer = percent / 100;

    if (this.state) {
      this.state.sceneDimmers = { ...this.state.sceneDimmers, [uuid]: dimmer };
    }

    clearTimeout(this.dimmerTimeouts.get(uuid));
    const waitMillis = Math.max(0, (this.dimmerSentMillis.get(uuid) || 0) + PlayScenesComponent.throttleMillis - Date.now());

    this.dimmerTimeouts.set(uuid, setTimeout(() => {
      this.dimmerTimeouts.delete(uuid);
      this.dimmerSentMillis.set(uuid, Date.now());
      this.scenesService
        .setSceneDimmer(tile.scene, dimmer, this.state)
        .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
        .subscribe();
    }, waitMillis));
  }

  stopComposition() {
    this.transportService.stop().subscribe();
  }

  // Dark text on a light color, light text on a dark one
  private readableOn(hex: string): string {
    const match = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex || '');

    if (!match) {
      return '#131519';
    }

    const luminance = [match[1], match[2], match[3]]
      .map((channel) => parseInt(channel, 16) / 255)
      .map((channel) => (channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)))
      .reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0);

    // where both reach about the same contrast
    return luminance > 0.18 ? '#131519' : '#ffffff';
  }

}
