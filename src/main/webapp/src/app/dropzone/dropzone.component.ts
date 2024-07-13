import { Component, EventEmitter, Input, Output } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";
import { DropzoneConfigInterface } from "ngx-dropzone-wrapper";
import { map } from "rxjs/operators";
import { AppHttpInterceptor } from "../app-http-interceptor/app-http-interceptor";

@Component({
  selector: "app-dropzone",
  templateUrl: "./dropzone.component.html",
  styleUrl: "./dropzone.component.scss",
})
export class DropzoneComponent {
  @Output() error = new EventEmitter();
  @Output() success = new EventEmitter();
  @Output() addedFile = new EventEmitter();

  private _url: string;
  private _acceptedFiles: string;
  private _maxFiles: number = undefined;

  @Input()
  set url(value: string) {
    this._url = value;
    this.updateConfig();
  }
  @Input()
  set acceptedFiles(value: string) {
    this._acceptedFiles = value;
    this.updateConfig();
  }
  @Input()
  set maxFiles(value: number) {
    this._maxFiles = value;
    this.updateConfig();
  }

  dropzoneConfig: DropzoneConfigInterface;
  uploadMessage: string;

  constructor(
    private translateService: TranslateService,
    private appHttpInterceptor: AppHttpInterceptor
  ) {
    this.translateService
      .get("editor.dropzone-message")
      .pipe(
        map((result) => {
          this.uploadMessage =
            '<h3 class="mb-0"><i class="fa fa-cloud-upload"></i></h3>' + result;
        })
      )
      .subscribe();

    this.updateConfig();
  }

  updateConfig() {
    this.dropzoneConfig = {
      url: this.appHttpInterceptor.getRestUrl() + this._url,
      paramName: "file",
      addRemoveLinks: false,
      maxFilesize: null /* unlimited */,
      acceptedFiles: this._acceptedFiles,
      timeout: 0,
      chunking: true,
      forceChunking: true,
      parallelUploads: 1,
      maxFiles: this._maxFiles,
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
      `,
    };
  }
}
