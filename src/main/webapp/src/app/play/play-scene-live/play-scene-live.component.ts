import { AfterViewInit, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { State } from '../../models/state';
import { ScenesService } from '../../services/scenes.service';
import { TempoService } from '../../services/tempo.service';
import { ToastGeneralErrorService } from '../../services/toast-general-error.service';

// The live controls of the scenes: back and GO through the scenes of the folder shown, the tempo the beat-synced
// effects run at (tapped, set, or the one of a MIDI clock or of what plays) with its beats, and the speed master
@Component({
  selector: 'app-play-scene-live',
  templateUrl: './play-scene-live.component.html',
  styleUrls: ['./play-scene-live.component.scss'],
  standalone: false
})
export class PlaySceneLiveComponent implements OnChanges, AfterViewInit, OnDestroy {
  // the most beats a bar is shown with
  private static readonly maxBeatsPerBar = 16;

  // the shortest time between two speeds sent while the speed master is dragged
  private static readonly throttleMillis = 100;

  @Input() state: State;

  // the speed being dragged (in percent), shown instead of the one of the device, which follows a moment later
  private draggedPercent: number;
  private sendTimeout: any;
  private lastSentMillis: number = 0;

  // the beats of the beat clock when the state arrived, and when (performance.now)
  private beatsAtState: number = 0;
  private stateArrivedAt: number = 0;

  // the dots of the beats of a bar, lit by an animation of their own (see showBeat)
  @ViewChildren('beatDot') beatDots: QueryList<ElementRef<HTMLElement>>;
  private animationFrame: number;
  private litBeat: number = -1;

  constructor(
    private scenesService: ScenesService,
    private tempoService: TempoService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private ngZone: NgZone,
  ) { }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.state && this.state) {
      // the beats run on from the ones the device sent, from the moment they arrived
      this.beatsAtState = this.state.tempoBeats || 0;
      this.stateArrivedAt = performance.now();
    }
  }

  ngAfterViewInit() {
    // the beats move many times a second: only the dots are changed, outside of Angular
    this.ngZone.runOutsideAngular(() => {
      const animate = () => {
        this.showBeat();
        this.animationFrame = requestAnimationFrame(animate);
      };

      this.animationFrame = requestAnimationFrame(animate);
    });
  }

  ngOnDestroy() {
    clearTimeout(this.sendTimeout);
    cancelAnimationFrame(this.animationFrame);
  }

  beatsPerBar(): number {
    return Math.min(Math.max(Math.round(this.state?.tempoBeatsPerBar || 4), 1), PlaySceneLiveComponent.maxBeatsPerBar);
  }

  // the beats of a bar, from the first one (0)
  beatIndices(): number[] {
    return Array.from({ length: this.beatsPerBar() }, (_, index) => index);
  }

  // The beat of the bar at the moment: the beats sent with the state, run on at the tempo since they arrived (none
  // without a tempo)
  currentBeat(now: number = performance.now()): number {
    const beatsPerMinute = this.state?.tempoBeatsPerMinute;

    if (!beatsPerMinute) {
      return -1;
    }

    const beats = this.beatsAtState + ((now - this.stateArrivedAt) * beatsPerMinute) / 60000;
    const beatsPerBar = this.beatsPerBar();

    return ((Math.floor(beats) % beatsPerBar) + beatsPerBar) % beatsPerBar;
  }

  // light the dot of the beat of the bar at the moment, the others dark
  private showBeat() {
    const beat = this.currentBeat();

    if (beat === this.litBeat || !this.beatDots) {
      return;
    }

    this.litBeat = beat;
    this.beatDots.forEach((dot, index) => dot.nativeElement.classList.toggle('scene-live-beat-on', index === beat));
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
    this.send(this.tempoService.tap());
  }

  // now is the first beat of a bar, also meant as the pointer goes down
  beatOne(event?: Event) {
    event?.preventDefault();
    this.send(this.tempoService.beatOne());
  }

  halve() {
    this.send(this.tempoService.halve());
  }

  double() {
    this.send(this.tempoService.double());
  }

  // the tempo, rounded to a tenth only where it isn't a whole one
  tempo(): string {
    const beatsPerMinute = this.state?.tempoBeatsPerMinute;

    if (!beatsPerMinute) {
      return '–';
    }

    return '' + Math.round(beatsPerMinute * 10) / 10;
  }

  tempoSource(): string {
    return this.state?.tempoSource || 'project';
  }

  // back to the tempo of the composition or the project playing
  resetTempo() {
    this.send(this.tempoService.set(undefined));
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
