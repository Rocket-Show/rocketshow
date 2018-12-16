import { TranslateService } from '@ngx-translate/core';
import { DropzoneConfigInterface } from 'ngx-dropzone-wrapper/dist/lib/dropzone.interfaces';
import { AppHttpInterceptor } from './../../../app-http-interceptor/app-http-interceptor';
import { BsModalRef } from 'ngx-bootstrap';
import { Composition } from './../../../models/composition';
import { Subject } from 'rxjs';
import { map } from "rxjs/operators";
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-editor-composition-lead-sheet',
  templateUrl: './editor-composition-lead-sheet.component.html',
  styleUrls: ['./editor-composition-lead-sheet.component.scss']
})
export class EditorCompositionLeadSheetComponent implements OnInit {

  onClose: Subject<number>;
  composition: Composition;

  dropzoneConfig: DropzoneConfigInterface;

  uploadMessage: string;

  constructor(
    private bsModalRef: BsModalRef,
    private appHttpInterceptor: AppHttpInterceptor,
    private translateService: TranslateService,
  ) {
    this.dropzoneConfig = {
      url: this.appHttpInterceptor.getRestUrl() + 'file/upload',
      addRemoveLinks: false,
      maxFilesize: 10000 /* 10 GB */,
      acceptedFiles: 'image/*',
      timeout: 0,
      previewTemplate: `
      <div class="dz-preview dz-file-preview">
        <!-- The attachment details -->
        <div class="dz-details" style="text-align: left">
          <i class="fa fa-file-o"></i> <span data-dz-name></span> <small><span class="label label-default file-size" data-dz-size></span></small>
        </div>
        
        <!--div class="mt-5">
          <span data-dz-errormessage></span>
        </div-->
        
        <div class="progress mt-4 mb-1" style="height: 10px">
          <div class="progress-bar progress-bar-striped progress-bar-animated" role="progressbar" style="width:0%;" data-dz-uploadprogress></div>
        </div>
      </div>
      `
    };

    this.translateService.get('editor.dropzone-message').pipe(map(result => {
      this.uploadMessage = '<h3 class="mb-0"><i class="fa fa-cloud-upload"></i></h3>' + result;
    })).subscribe();
  }

  ngOnInit() {
    this.onClose = new Subject();
  }

  public onUploadError(args: any) {
    console.log('Upload error', args);
  }

  public onUploadSuccess(args: any) {
    // Hide the preview element
    args[0].previewElement.hidden = true;

    console.log(args[1]);
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }

}
