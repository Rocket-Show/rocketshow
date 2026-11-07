import { Component, Input, OnDestroy } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { State } from '../../models/state';
import { ScenesService } from '../../services/scenes.service';
import { ToastGeneralErrorService } from '../../services/toast-general-error.service';

// The live controls of the scenes: back and GO through the scenes of the folder shown, the tempo they run at (tapped,
// or the one of a MIDI clock or of the project) and the speed master
@Component({
  selector: 'app-play-scene-live',
  templateUrl: './play-scene-live.component.html',
  styleUrls: ['./play-scene-live.component.scss'],
  standalone: false
})
export class PlaySceneLiveComponent implements OnDestroy {
  // the shortest time between two speeds sent while the speed master is dragged
  private static readonly throttleMillis = 100;

  @Input() state: State;

  // the speed being dragged (in percent), shown instead of the one of the device, which follows a moment later
  private draggedPercent: number;
  private sendTimeout: any;
  private lastSentMillis: number = 0;

  constructor(
    private scenesService: ScenesService,
    private toastGeneralErrorService: ToastGeneralErrorService,
  ) { }

  ngOnDestroy() {
    clearTimeout(this.sendTimeout);
  }

  private send(request: any) {
    request.pipe(catchError((err) => this.toastGeneralErrorService.show(err))).subscribe();
  }

  hasProject(): boolean {
    return !!this.scenesService.viewedProject(this.state);
  }

  previous() {
    this.send(this.scenesService.step(this.scenesService.viewedProject(this.state), -1));
  }

  next() {
    this.send(this.scenesService.step(this.scenesService.viewedProject(this.state), 1));
  }

  // tapped as the pointer goes down, which is when the beat is meant (a click comes only when it goes up again)
  tap(event?: Event) {
    event?.preventDefault();
    this.send(this.scenesService.tapTempo());
  }

  // the tempo, rounded to a tenth only where it isn't a whole one
  tempo(): string {
    const beatsPerMinute = this.state?.sceneBeatsPerMinute;

    if (!beatsPerMinute) {
      return '–';
    }

    return '' + Math.round(beatsPerMinute * 10) / 10;
  }

  tempoSource(): string {
    return this.state?.sceneTempoSource || 'project';
  }

  // back to the tempo of the project
  resetTempo() {
    this.send(this.scenesService.setTempo(undefined));
  }

  speedPercent(): number {
    if (this.sendTimeout) {
      return this.draggedPercent;
    }

    return Math.round((this.state?.sceneSpeed ?? 1) * 100);
  }

  // Send the speed while dragging, but not more often than the throttle allows. The last one is always sent.
  setSpeedPercent(percent: number) {
    this.draggedPercent = percent;
    clearTimeout(this.sendTimeout);
    const waitMillis = Math.max(0, this.lastSentMillis + PlaySceneLiveComponent.throttleMillis - Date.now());

    this.sendTimeout = setTimeout(() => {
      this.sendTimeout = undefined;
      this.lastSentMillis = Date.now();

      if (this.state) {
        this.state.sceneSpeed = this.draggedPercent / 100;
      }

      this.send(this.scenesService.setSpeed(this.draggedPercent / 100));
    }, waitMillis);
  }

  resetSpeed() {
    clearTimeout(this.sendTimeout);
    this.sendTimeout = undefined;

    if (this.state) {
      this.state.sceneSpeed = 1;
    }

    this.send(this.scenesService.setSpeed(1));
  }
}
