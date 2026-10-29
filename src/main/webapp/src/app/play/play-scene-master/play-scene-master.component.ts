import { Component, Input, OnDestroy } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { State } from '../../models/state';
import { ScenesService } from '../../services/scenes.service';
import { ToastGeneralErrorService } from '../../services/toast-general-error.service';

// The master dimmer of the scenes lit by hand, a fader wide enough to be dragged with a finger
@Component({
  selector: 'app-play-scene-master',
  templateUrl: './play-scene-master.component.html',
  styleUrls: ['./play-scene-master.component.scss'],
  standalone: false
})
export class PlaySceneMasterComponent implements OnDestroy {

  // the shortest time between two values sent while dragging. Every value is saved with the session of the device.
  private static readonly throttleMillis = 100;

  @Input() state: State;

  // the value being dragged (0 to 100), shown instead of the one of the device, which follows a moment later
  private draggedPercent: number;
  private dragging: boolean = false;

  private sendTimeout: any;
  private lastSentMillis: number = 0;

  constructor(
    private scenesService: ScenesService,
    private toastGeneralErrorService: ToastGeneralErrorService,
  ) { }

  ngOnDestroy() {
    clearTimeout(this.sendTimeout);
  }

  percent(): number {
    if (this.dragging || this.sendTimeout) {
      return this.draggedPercent;
    }

    return Math.round((this.state?.sceneMasterDimmer ?? 1) * 100);
  }

  startDrag() {
    this.dragging = true;
    this.draggedPercent = this.percent();
  }

  endDrag() {
    this.dragging = false;
  }

  // Send the values while dragging, but not more often than the throttle allows. The last one is always sent.
  input(percent: number) {
    this.draggedPercent = percent;

    clearTimeout(this.sendTimeout);
    const waitMillis = Math.max(0, this.lastSentMillis + PlaySceneMasterComponent.throttleMillis - Date.now());

    this.sendTimeout = setTimeout(() => {
      this.sendTimeout = undefined;
      this.lastSentMillis = Date.now();

      if (this.state) {
        this.state.sceneMasterDimmer = this.draggedPercent / 100;
      }

      this.scenesService
        .setMasterDimmer(this.draggedPercent / 100)
        .pipe(catchError((err) => this.toastGeneralErrorService.show(err)))
        .subscribe();
    }, waitMillis);
  }

}
