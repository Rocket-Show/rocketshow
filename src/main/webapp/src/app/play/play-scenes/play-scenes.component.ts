import { Component, Input } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { DesignerScene, DesignerSceneFolder, SceneProject } from '../../models/scene-project';
import { State } from '../../models/state';
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
}

// The scenes of a designer project as large tiles, lit by hand with a tap
@Component({
  selector: 'app-play-scenes',
  templateUrl: './play-scenes.component.html',
  styleUrls: ['./play-scenes.component.scss'],
  standalone: false
})
export class PlayScenesComponent {

  // what a lit scene without a color of its own is shown with
  private static readonly fallbackColor = '#fd7e14';

  @Input() state: State;

  constructor(
    public scenesService: ScenesService,
    private transportService: TransportService,
    private toastGeneralErrorService: ToastGeneralErrorService,
  ) { }

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
      const color = this.scenesService.color(scene) || PlayScenesComponent.fallbackColor;

      return {
        scene,
        icon: this.scenesService.icon(scene),
        on,
        held: on && (this.state.sceneBlackout || this.state.scenesHeld),
        color,
        textColor: this.readableOn(color),
        dimmerPercent: Math.round(scene.dimmer * 100),
      };
    });
  }

  trackTile(index: number, tile: SceneTile): string {
    return tile.scene.uuid;
  }

  tap(tile: SceneTile) {
    this.scenesService
      .tap(tile.scene, this.project(), this.state)
      .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
      .subscribe();
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
