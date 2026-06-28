import { Subscription } from 'rxjs';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { UpdateState } from '../models/update-state';
import { UpdateService } from '../services/update.service';
import { UpdateStateService } from '../services/update-state.service';
import { ReloadClearCacheService } from '../services/reload-clear-cache.service';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';

// Full-screen update status, shown while an update is in progress. It survives a
// page reload (the app detects the ongoing update and renders this component), so
// the user always sees the current status until the update is done or failed and
// cannot navigate away from it in the meantime.
@Component({
  selector: 'app-update-progress',
  templateUrl: './update-progress.component.html',
  styleUrls: ['./update-progress.component.scss'],
  standalone: false
})
export class UpdateProgressComponent implements OnInit, OnDestroy {

  updateStep: string = 'settings.update-install';
  updatePerc: number = 0;
  updateFinished: boolean = false;
  updateError: boolean = false;

  private updateStateServiceSubscription: Subscription;
  private pollStateInterval: any;

  constructor(
    private updateService: UpdateService,
    private updateStateService: UpdateStateService,
    private reloadClearCacheService: ReloadClearCacheService,
    private toastGeneralErrorService: ToastGeneralErrorService,
  ) { }

  ngOnInit() {
    this.updateStateServiceSubscription = this.updateStateService.updateState.subscribe((updateState: UpdateState) => {
      this.processUpdateState(updateState);
    });

    // Seed the current state, because the websocket only pushes future changes
    // (e.g. right after a page reload there might not be a new state for a while).
    this.updateService.getUpdateState().subscribe((updateState) => {
      this.processUpdateState(updateState);
    });
  }

  private finishUpdateSuccess() {
    this.updateFinished = true;
    this.updatePerc = 100;
  }

  private finishUpdateError(error: string) {
    this.updateError = true;
    this.toastGeneralErrorService.show(new Error(error));
  }

  private processUpdateState(updateState: UpdateState) {
    if (updateState.step === 'REBOOTING' || updateState.step === 'FALLING_BACK') {
      this.updateStep = 'settings.update-reboot';
      this.updatePerc = 99;

      // Don't rely on states pushed from the backend, because it's rebooting now
      // and we might not be able to reconnect to the websocket in time. Instead, poll
      // for a new status until the update is finished.
      if (this.updateStateServiceSubscription) {
        this.updateStateServiceSubscription.unsubscribe();
        this.updateStateServiceSubscription = undefined;
      }

      if (!this.pollStateInterval) {
        this.pollStateInterval = setInterval(() => {
          this.updateService.getUpdateState().subscribe((polledState) => {
            if (polledState.step === 'FINISHED') {
              clearInterval(this.pollStateInterval);
              this.pollStateInterval = undefined;
            }
            this.processUpdateState(polledState);
          });
        }, 5000);
      }
    } else if (updateState.step === 'FINISHED') {
      if (updateState.error) {
        this.finishUpdateError(updateState.error);
      } else {
        this.finishUpdateSuccess();
      }
    } else {
      this.updateStep = 'settings.update-install';
      if (updateState.progressPercentage > 98) {
        this.updatePerc = 98;
      } else {
        this.updatePerc = updateState.progressPercentage;
      }
    }
  }

  public reload(): void {
    // Reload the page (caching has been disabled in Angular CLI and we
    // therefore automatically receive the new version of the app)
    this.reloadClearCacheService.reload();
  }

  ngOnDestroy() {
    if (this.updateStateServiceSubscription) {
      this.updateStateServiceSubscription.unsubscribe();
    }
    if (this.pollStateInterval) {
      clearInterval(this.pollStateInterval);
    }
  }

}
