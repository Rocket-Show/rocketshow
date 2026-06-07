import { DiskSpaceService } from "../../services/disk-space.service";
import { WarningDialogService } from "../../services/warning-dialog.service";
import { FileService } from "../../services/file.service";
import { CompositionFile } from "../../models/composition-file";
import { ChangeDetectorRef, Component, OnInit } from "@angular/core";
import { map } from "rxjs/operators";

@Component({
    selector: "app-editor-media",
    templateUrl: "./editor-media.component.html",
    styleUrls: ["./editor-media.component.scss"],
    standalone: false
})
export class EditorMediaComponent implements OnInit {

  existingFiles: CompositionFile[] = [];
  filteredExistingFiles: CompositionFile[] = [];

  diskSpaceUsedGB: number = 0;
  diskSpaceAvailableGB: number = 0;
  diskSpacePercentage: number = 0;

  constructor(
    private fileService: FileService,
    private warningDialogService: WarningDialogService,
    private diskSpaceService: DiskSpaceService,
    public changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadFiles();
    this.loadDiskSpace();
  }

  private loadDiskSpace() {
    this.diskSpaceService
      .getDiskSpace()
      .pipe(
        map((diskSpace) => {
          this.diskSpaceUsedGB = Math.round((diskSpace.usedMB / 1024) * 100) / 100;
          this.diskSpaceAvailableGB = Math.round(((diskSpace.usedMB + diskSpace.availableMB) / 1024) * 100) / 100;

          if (diskSpace.usedMB != 0) {
            this.diskSpacePercentage = Math.round(
              (100 * diskSpace.usedMB) / (diskSpace.usedMB + diskSpace.availableMB)
            );
          }
        })
      )
      .subscribe();
  }

  private loadFiles() {
    this.fileService
      .getFiles()
      .pipe(
        map((result) => {
          this.existingFiles = result;
          this.filterFiles();
        })
      )
      .subscribe();
  }

  filterFiles(searchValue?: string) {
    if (!searchValue) {
      this.filteredExistingFiles = this.existingFiles;
      return;
    }

    this.filteredExistingFiles = this.existingFiles.filter(
      (file) => file.name.toLowerCase().indexOf(searchValue.toLowerCase()) !== -1
    );
  }

  onUploadError(args: any) {
    console.log("Upload error", args);
  }

  onUploadSuccess(args: any) {
    args[0].previewElement.hidden = true;
    this.loadFiles();
    this.loadDiskSpace();
  }

  deleteFile(existingFile: CompositionFile) {
    this.warningDialogService
      .show("editor.warning-delete-file")
      .pipe(
        map((result) => {
          if (result) {
            this.fileService.deleteFile(existingFile).subscribe(() => {
              this.loadFiles();
              this.loadDiskSpace();
            });
          }
        })
      )
      .subscribe();
  }
}
