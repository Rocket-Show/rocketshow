import { ToastrService } from "ngx-toastr";
import { TranslateService } from "@ngx-translate/core";
import { Injectable } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { throwError } from "rxjs";

@Injectable()
export class ToastGeneralErrorService {
  constructor(
    private translateService: TranslateService,
    private toastrService: ToastrService
  ) {}

  showMessage(message: string): any {
    this.translateService
      .get([
        "settings.toast-general-error",
        "settings.toast-general-error-title",
      ])
      .subscribe((result) => {
        let text = result["settings.toast-general-error"];

        if (message) {
          text += "<hr /><small>" + message + "</small>";
        }

        this.toastrService.error(
          text,
          result["settings.toast-general-error-title"],
          { timeOut: 0, extendedTimeOut: 0, enableHtml: true }
        );
      });
  }

  show(error?: Error): any {
    let message: string;
    let showMessageAsync = false;

    if (error) {
      if (error instanceof HttpErrorResponse) {
        if (error.error instanceof Blob) {
          // The error response is a Blob
          const reader = new FileReader();
          reader.onload = (event: any) => {
            message = JSON.parse(event.target.result).message;
            this.showMessage(message);
          };
          reader.readAsText(error.error);
          showMessageAsync = true;
        } else {
          message = (<HttpErrorResponse>error).error.message;
        }
      } else {
        message = error.message;
      }
    }

    if (!showMessageAsync) {
      this.showMessage(message);
    }

    return throwError(error);
  }
}
