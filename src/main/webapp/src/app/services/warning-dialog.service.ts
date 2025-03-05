import { Observable, Subject } from "rxjs";
import { map, mergeMap } from "rxjs/operators";
import { BsModalService } from "ngx-bootstrap/modal";
import { Injectable } from "@angular/core";
import { WarningDialogComponent } from "../warning-dialog/warning-dialog.component";
import { TranslateService } from "@ngx-translate/core";

@Injectable()
export class WarningDialogService {
  constructor(
    private modalService: BsModalService,
    private translateService: TranslateService
  ) {}

  // Show a warning dialog and return true, when the user clicked OK
  show(message: string): Observable<boolean> {
    return this.translateService.get(message).pipe(
      mergeMap((result) => {
        let warningDialog = <WarningDialogComponent>this.modalService.show(
          WarningDialogComponent,
          {
            keyboard: false,
            ignoreBackdropClick: true,
          }
        ).content;
        warningDialog.message = result;

        return warningDialog.onClose.pipe(
          map((result) => {
            if (result === 1) {
              return true;
            }

            return false;
          })
        );
      })
    );
  }
}
