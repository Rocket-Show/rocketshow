import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { BsModalService } from "ngx-bootstrap/modal";
import { EMPTY } from "rxjs";
import { catchError, filter, finalize, switchMap } from "rxjs/operators";
import { SelfTestResult } from "../models/self-test-result";
import { SelfTestResultDialogComponent } from "../settings/self-test-result-dialog/self-test-result-dialog.component";
import { ToastGeneralErrorService } from "./toast-general-error.service";
import { WaitDialogService } from "./wait-dialog.service";
import { WarningDialogService } from "./warning-dialog.service";

// The self-test of the unit, from the advanced settings and the provisioning
@Injectable()
export class SelfTestService {
  constructor(
    private http: HttpClient,
    private warningDialogService: WarningDialogService,
    private waitDialogService: WaitDialogService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private modalService: BsModalService
  ) {}

  // Plays test tones and switches outputs, so only after a warning
  run(): void {
    this.warningDialogService
      .show("settings.self-test.warning")
      .pipe(
        filter((result) => result),
        switchMap(() => {
          this.waitDialogService.show("settings.self-test.wait");

          return this.http.post("system/test", {}).pipe(
            finalize(() => {
              this.waitDialogService.hide();
            }),
            catchError((error) => {
              // E.g. a composition plays
              this.toastGeneralErrorService.show(error);
              return EMPTY;
            })
          );
        })
      )
      .subscribe((result) => {
        // The result replaces the wait dialog
        this.waitDialogService.hide();
        this.modalService.show(SelfTestResultDialogComponent, {
          keyboard: true,
          ignoreBackdropClick: true,
          class: "modal-lg",
          initialState: { selfTestResult: new SelfTestResult(result) },
        });
      });
  }
}
