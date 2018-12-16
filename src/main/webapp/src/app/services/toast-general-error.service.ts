import { Observable } from 'rxjs';
import { ToastrService } from 'ngx-toastr';
import { TranslateService } from '@ngx-translate/core';
import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable()
export class ToastGeneralErrorService {

  constructor(
    private translateService: TranslateService,
    private toastrService: ToastrService) { }

  show(error?: Error): any {
    this.translateService.get(['settings.toast-general-error', 'settings.toast-general-error-title']).subscribe(result => {
      let text = result['settings.toast-general-error'];

      if(error) {
        let message: string;

        if(error instanceof HttpErrorResponse) {
          message = (<HttpErrorResponse>error).error.message;
        } else {
          message = error.message;
        }

        text += '<hr /><small>' + message + '</small>';
      }

      this.toastrService.error(text, result['settings.toast-general-error-title'], {timeOut: 0, extendedTimeOut: 0, enableHtml: true});
    });

    return Observable.throw(error);
  }

}
