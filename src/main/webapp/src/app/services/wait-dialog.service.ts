import { TranslateService } from "@ngx-translate/core";
import { BsModalService } from "ngx-bootstrap/modal";
import { Injectable } from "@angular/core";
import { WaitDialogComponent } from "../wait-dialog/wait-dialog.component";
import { map } from "rxjs/operators";

@Injectable()
export class WaitDialogService {
  private fileDialog: any;

  constructor(
    private modalService: BsModalService,
    private translateService: TranslateService
  ) {}

  // Show a wait dialog
  show(message: string): void {
    this.translateService
      .get(message)
      .pipe(
        map((result) => {
          this.fileDialog = this.modalService.show(WaitDialogComponent, {
            keyboard: false,
            ignoreBackdropClick: true,
          });
          (<WaitDialogComponent>this.fileDialog.content).message = result;
        })
      )
      .subscribe();
  }

  hide(): void {
    if (!this.fileDialog) {
      return;
    }
    this.fileDialog.hide();
  }
}
