import { StateService } from './../services/state.service';
import { UpdateService } from './../services/update.service';
import { Subject, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { State } from '../models/state';
import { Version } from '../models/version';

@Component({
  selector: 'app-update-dialog',
  templateUrl: './update-dialog.component.html',
  styleUrls: ['./update-dialog.component.scss']
})
export class UpdateDialogComponent implements OnInit, OnDestroy {
  onClose: Subject<number>;
  currentVersion: Version;
  remoteVersion: Version;
  errorRetreiveRemoteVersion: boolean = false;
  remoteVersionNewer: boolean = false;
  updateStep: string;
  updatePerc: number;
  updating: boolean = false;
  waitReboot: boolean = false;
  updateFinished: boolean = false;

  stateServiceSubscription: Subscription;

  constructor(
    private bsModalRef: BsModalRef,
    public updateService: UpdateService,
    private stateService: StateService) { }

  ngOnInit() {
    this.onClose = new Subject();

    this.stateServiceSubscription =  this.stateService.state.subscribe((state: State) => {
      switch (state.updateState) {
        case 'DOWNLOADING': {
          this.updateStep = 'settings.update-download';
          this.updatePerc = 10;
          break;
        }
        case 'INSTALLING': {
          this.updateStep = 'settings.update-install';
          this.updatePerc = 70;
          break;
        }
        case 'REBOOTING': {
          this.updateStep = 'settings.update-reboot';
          this.updatePerc = 80;
          this.waitReboot = true;
        }
      }

      if (this.waitReboot) {
        if (state.updateFinished) {
          // The update has been completed and the update is finished
          this.updating = false;
          this.updateFinished = true;
          this.updatePerc = 100;

          this.updateService.finishUpdate().subscribe();
        }
      }
    });

    this.updateService.getCurrentVersion().subscribe((version: Version) => {
      this.currentVersion = version;
    });

    this.retreiveRemoteVersion();
  }

  retreiveRemoteVersion() {
    this.errorRetreiveRemoteVersion = false;
    this.remoteVersionNewer = false;

    this.updateService.getRemoteVersion().pipe(catchError(() => {
      this.errorRetreiveRemoteVersion = true;
      return undefined;
    }))
      .subscribe((version: Version) => {
        this.remoteVersion = version;

        if (this.versionCompare(this.remoteVersion.version, this.currentVersion.version) > 0) {
          this.remoteVersionNewer = true;

          // Remove all change notes from the remote version, already included in the current one
          for (var i = 0; i < this.remoteVersion.changeNoteList.length; ++i) {
            let changeNote = this.remoteVersion.changeNoteList[i];

            if(this.versionCompare(changeNote.version, this.currentVersion.version) <= 0) {
              this.remoteVersion.changeNoteList.splice(i--, 1);
            }
          }
        }
      });
  }

  ngOnDestroy() {
    this.stateServiceSubscription.unsubscribe();
  }

  public update() {
    // Perform the update
    this.updating = true;
    this.waitReboot = false;
    this.updateFinished = false;
    this.updateStep = 'settings.update-start';
    this.updatePerc = 0;

    this.updateService.doUpdate().subscribe();
  }

  // Taken from https://stackoverflow.com/questions/6832596/how-to-compare-software-version-number-using-js-only-number
  private versionCompare(v1: string, v2: string, options?) {
    let lexicographical = options && options.lexicographical;
    let zeroExtend = options && options.zeroExtend;
    let v1parts: any[] = v1.split('.');
    let v2parts: any[] = v2.split('.');

    function isValidPart(x) {
      return (lexicographical ? /^\d+[A-Za-z]*$/ : /^\d+$/).test(x);
    }

    if (!v1parts.every(isValidPart) || !v2parts.every(isValidPart)) {
      return NaN;
    }

    if (zeroExtend) {
      while (v1parts.length < v2parts.length) v1parts.push("0");
      while (v2parts.length < v1parts.length) v2parts.push("0");
    }

    if (!lexicographical) {
      v1parts = v1parts.map(Number);
      v2parts = v2parts.map(Number);
    }

    for (var i = 0; i < v1parts.length; ++i) {
      if (v2parts.length == i) {
        return 1;
      }

      if (v1parts[i] == v2parts[i]) {
        continue;
      }
      else if (v1parts[i] > v2parts[i]) {
        return 1;
      }
      else {
        return -1;
      }
    }

    if (v1parts.length != v2parts.length) {
      return -1;
    }

    return 0;
  }

  public ok(): void {
    if (this.updateFinished) {
      // Reload the page (caching has been disabled in Angular CLI and we
      // therefore automatically receive the new version of the app)
      location.reload();
    } else {
      this.onClose.next(1);
      this.bsModalRef.hide();
    }
  }

}
