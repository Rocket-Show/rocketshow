import { StateService } from './../../services/state.service';
import { UpdateService } from './../../services/update.service';
import { Subject, Subscription, EMPTY } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { State } from '../../models/state';
import { Version } from '../../models/version';
import { ReloadClearCacheService } from '../../services/reload-clear-cache.service';
import { UpdateState } from '../../models/update-state';
import { ToastGeneralErrorService } from '../../services/toast-general-error.service';
import { UpdateStateService } from '../../services/update-state.service';

@Component({
  selector: 'app-update-dialog',
  templateUrl: './update-dialog.component.html',
  styleUrls: ['./update-dialog.component.scss'],
  standalone: false
})
export class UpdateDialogComponent implements OnInit, OnDestroy {
  onClose: Subject<number>;
  currentVersion: Version;
  remoteVersion: Version;
  errorRetreiveRemoteVersion: boolean = false;
  noInternetError: boolean = false;
  remoteVersionNewer: boolean = false;
  updateStep: string;
  updatePerc: number;
  updating: boolean = false;
  updateFinished: boolean = false;

  updateStateServiceSubscription: Subscription;
  pollStateInterval: any;

  constructor(
    private bsModalRef: BsModalRef,
    public updateService: UpdateService,
    private reloadClearCacheService: ReloadClearCacheService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private updateStateService: UpdateStateService,
  ) { }

  ngOnInit() {
    this.onClose = new Subject();

    this.updateStateServiceSubscription = this.updateStateService.updateState.subscribe((updateState: UpdateState) => {
      this.processUpdateState(updateState);
    });

    this.updateService.getCurrentVersion().subscribe((version: Version) => {
      this.currentVersion = version;
      this.retreiveRemoteVersion();
    });
  }

  private finishUpdateSuccess() {
    // The update has been completed and the update is finished
    this.updating = false;
    this.updateFinished = true;
    this.updatePerc = 100;
  }

  private finishUpdateError(error: string) {
    this.toastGeneralErrorService.show(new Error(error))
    this.updating = false;
  }

  private processUpdateState(updateState: UpdateState) {
    if (updateState.step === 'REBOOTING' || updateState.step === 'FALLING_BACK') {
      this.updateStep = 'settings.update-reboot';
      this.updatePerc = 99;

      // Don't rely on states pushed from the backend, because it's rebooting now
      // and we might not be able to reconnect to the websocket in time. Instead, poll for a new
      // status.
      if (this.updateStateServiceSubscription) {
        this.updateStateServiceSubscription.unsubscribe();
      }

      this.pollStateInterval = setInterval(() => {
        this.updateService.getUpdateState().subscribe((updateState) => {
          // Reboot finished
          clearInterval(this.pollStateInterval);
          this.processUpdateState(updateState);
        });
      }, 5000);
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
        this.updatePerc = updateState.progressPercentage
      }
    }
  }

  retreiveRemoteVersion() {
    this.errorRetreiveRemoteVersion = false;
    this.noInternetError = false;
    this.remoteVersionNewer = false;

    this.updateService.getRemoteVersion().pipe(catchError((error) => {
      if (error.status === 503) {
        this.noInternetError = true;
      } else {
        this.errorRetreiveRemoteVersion = true;
      }
      return EMPTY;
    }))
      .subscribe((version: Version) => {
        this.remoteVersion = version;

        if (this.updateService.isVersionNewer(this.remoteVersion, this.currentVersion)) {
          this.remoteVersionNewer = true;

          // Remove all change notes from the remote version, already included in the current one
          for (var i = 0; i < this.remoteVersion.changeNoteList.length; ++i) {
            let changeNote = this.remoteVersion.changeNoteList[i];

            if (!this.updateService.isVersionStringNewer(changeNote.version, this.currentVersion.version)) {
              this.remoteVersion.changeNoteList.splice(i--, 1);
            }
          }
        }
      });
  }

  ngOnDestroy() {
    if (this.updateStateServiceSubscription) {
      this.updateStateServiceSubscription.unsubscribe();
    }
    if (this.pollStateInterval) {
      clearInterval(this.pollStateInterval);
    }
  }

  public update() {
    // Perform the update
    this.updating = true;
    this.updateFinished = false;
    this.updateStep = 'settings.update-start';
    this.updatePerc = 0;

    this.updateService.doUpdate().subscribe();
  }

  public ok(): void {
    if (this.updateFinished) {
      // Reload the page (caching has been disabled in Angular CLI and we
      // therefore automatically receive the new version of the app)
      this.reloadClearCacheService.reload();
    } else {
      this.onClose.next(1);
      this.bsModalRef.hide();
    }
  }

}
