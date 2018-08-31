import { Observable } from 'rxjs/Rx';
import { ToastrService } from 'ngx-toastr';
import { TranslateService } from '@ngx-translate/core';
import { Injectable } from '@angular/core';

@Injectable()
export class ToastGeneralErrorService {

  constructor(
    private translateService: TranslateService,
    private toastrService: ToastrService) { }

  show(error?: any): any {
    this.translateService.get(['settings.toast-general-error', 'settings.toast-general-error-title']).subscribe(result => {
      let text = result['settings.toast-general-error'];

      if(error) {
        text += '<hr /><small>HTTP ' + error.status + ': ' + error.error + '</small>';
      }

      this.toastrService.error(text, result['settings.toast-general-error-title'], {timeOut: 0, extendedTimeOut: 0, enableHtml: true});
    });

    return Observable.throw(error);
  }

}
