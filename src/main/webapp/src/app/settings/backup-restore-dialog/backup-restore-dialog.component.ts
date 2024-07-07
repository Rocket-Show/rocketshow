import { Component, OnDestroy, OnInit } from "@angular/core";
import { BsModalRef } from "ngx-bootstrap/modal";
import { Subject, Subscription } from "rxjs";
import { StateService } from "../../services/state.service";
import { WaitDialogService } from "../../services/wait-dialog.service";
import { map } from "rxjs/operators";
import { State } from "../../models/state";
import { InfoDialogService } from "../../services/info-dialog.service";

@Component({
  selector: "app-backup-restore-dialog",
  templateUrl: "./backup-restore-dialog.component.html",
  styleUrl: "./backup-restore-dialog.component.scss",
})
export class BackupRestoreDialogComponent implements OnInit, OnDestroy {
  onClose: Subject<number>;
  uploading: boolean = false;

  private stateChangedSubscription: Subscription;
  private isRestoringBackup: boolean = false;

  constructor(
    private bsModalRef: BsModalRef,
    private stateService: StateService,
    private waitDialogService: WaitDialogService,
    private infoDialogService: InfoDialogService
  ) {}

  ngOnInit() {
    this.onClose = new Subject();

    this.stateChangedSubscription = this.stateService.state.subscribe(
      (state: State) => {
        if (this.isRestoringBackup) {
          // We got a new state after restoring the backup
          // -> the device has been resetted
          this.isRestoringBackup = false;

          this.infoDialogService
            .show("settings.backup.restore-done")
            .pipe(
              map(() => {
                location.reload();
              })
            )
            .subscribe();
        }
      }
    );
  }

  ngOnDestroy() {
    this.stateChangedSubscription.unsubscribe();
  }

  public cancel(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public onAddedFile(args: any) {
    this.uploading = true;
  }

  public onUploadError(args: any) {
    this.uploading = false;
    console.log("Upload error", args);
  }

  public onUploadSuccess(args: any) {
    this.uploading = false;
    this.waitDialogService.show("settings.backup.wait-restore");
    this.isRestoringBackup = true;
    this.bsModalRef.hide();
  }
}
