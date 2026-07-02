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
    // The websocket gives smooth progress while installing.
    this.updateStateServiceSubscription = this.updateStateService.updateState.subscribe((updateState: UpdateState) => {
      this.processUpdateState(updateState);
    });

    // Poll the current state as a reliable fallback. The websocket only pushes
    // future changes, so it can miss the final state after a reboot: e.g. when an
    // update is interrupted while installing, the backend marks it as failed on
    // startup before any client is connected, and no state is pushed afterwards.
    // Seed immediately and keep polling until the update is done or has failed.
    this.pollUpdateState();
    this.pollStateInterval = setInterval(() => this.pollUpdateState(), 5000);
  }

  private pollUpdateState() {
    this.updateService.getUpdateState().subscribe((updateState) => {
      this.processUpdateState(updateState);
    });
  }

  private stopPolling() {
    if (this.pollStateInterval) {
      clearInterval(this.pollStateInterval);
      this.pollStateInterval = undefined;
    }
  }

  private finishUpdateSuccess() {
    this.updateFinished = true;
    this.updatePerc = 100;
    this.stopPolling();
  }

  private finishUpdateError(error: string) {
    this.updateError = true;
    this.stopPolling();
    this.toastGeneralErrorService.show(new Error(error));
  }

  private processUpdateState(updateState: UpdateState) {
    if (updateState.step === 'FINISHED') {
      if (this.updateFinished || this.updateError) {
        // Already handled (e.g. by a concurrent websocket message and poll).
        return;
      }
      if (updateState.error) {
        this.finishUpdateError(updateState.error);
      } else {
        this.finishUpdateSuccess();
      }
    } else if (updateState.step === 'REBOOTING' || updateState.step === 'FALLING_BACK') {
      this.updateStep = 'settings.update-reboot';
      this.updatePerc = 99;
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
