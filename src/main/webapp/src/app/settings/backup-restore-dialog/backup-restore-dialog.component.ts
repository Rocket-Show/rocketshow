import { Component } from "@angular/core";
import { BsModalRef } from "ngx-bootstrap/modal";
import { DropzoneConfigInterface } from "ngx-dropzone-wrapper";
import { Subject } from "rxjs";
import { AppHttpInterceptor } from "../../app-http-interceptor/app-http-interceptor";
import { TranslateService } from "@ngx-translate/core";
import { map } from "rxjs/operators";

@Component({
  selector: "app-backup-restore-dialog",
  templateUrl: "./backup-restore-dialog.component.html",
  styleUrl: "./backup-restore-dialog.component.scss",
})
export class BackupRestoreDialogComponent {
  onClose: Subject<number>;
  uploading: boolean = false;
  dropzoneConfig: DropzoneConfigInterface;
  uploadMessage: string;

  constructor(
    private bsModalRef: BsModalRef,
    private appHttpInterceptor: AppHttpInterceptor,
    private translateService: TranslateService
  ) {
    this.dropzoneConfig = {
      url: this.appHttpInterceptor.getRestUrl() + "system/restore-backup",
      paramName: "file",
      addRemoveLinks: false,
      maxFilesize: 1000000 /* 1 TB */,
      acceptedFiles: ".zip",
      timeout: 0,
      chunking: true,
      forceChunking: true,
      parallelUploads: 1,
      maxFiles: 1,
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

    this.translateService
      .get("editor.dropzone-message")
      .pipe(
        map((result) => {
          this.uploadMessage =
            '<h3 class="mb-0"><i class="fa fa-cloud-upload"></i></h3>' + result;
        })
      )
      .subscribe();
  }

  ngOnInit() {
    this.onClose = new Subject();
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
    console.log("Upload success", args);
  }
}
