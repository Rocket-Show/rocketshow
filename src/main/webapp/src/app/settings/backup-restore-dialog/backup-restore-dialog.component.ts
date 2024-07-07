import { Component } from "@angular/core";
import { BsModalRef } from "ngx-bootstrap/modal";
import { DropzoneConfigInterface } from "ngx-dropzone-wrapper";
import { Subject } from "rxjs";

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

  constructor(private bsModalRef: BsModalRef) {}

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
