import { TranslateService } from '@ngx-translate/core';
import { DropzoneConfigInterface } from 'ngx-dropzone-wrapper/dist/lib/dropzone.interfaces';
import { AppHttpInterceptor } from './../../../app-http-interceptor/app-http-interceptor';
import { BsModalRef } from 'ngx-bootstrap';
import { Composition } from './../../../models/composition';
import { Subject } from 'rxjs';
import { map } from "rxjs/operators";
import { Component, OnInit } from '@angular/core';
import { LeadSheet } from '../../../models/lead-sheet';
import { WarningDialogService } from '../../../services/warning-dialog.service';
import { DiskSpaceService } from '../../../services/disk-space.service';
import { LeadSheetService } from '../../../services/lead-sheet.service';
import { Settings } from '../../../models/settings';
import { SettingsService } from '../../../services/settings.service';

@Component({
  selector: 'app-editor-composition-lead-sheet',
  templateUrl: './editor-composition-lead-sheet.component.html',
  styleUrls: ['./editor-composition-lead-sheet.component.scss']
})
export class EditorCompositionLeadSheetComponent implements OnInit {

  selectUndefinedOptionValue: any = undefined;

  leadSheetIndex: number;
  leadSheet: LeadSheet;
  settings: Settings;

  onClose: Subject<number>;
  composition: Composition;

  existingLeadSheets: LeadSheet[] = [];
  filteredExistingLeadSheets: LeadSheet[] = [];

  dropzoneConfig: DropzoneConfigInterface;

  uploadMessage: string;

  diskSpaceUsedGB: number = 0;
  diskSpaceAvailableGB: number = 0;
  diskSpacePercentage: number = 0;

  constructor(
    private bsModalRef: BsModalRef,
    private appHttpInterceptor: AppHttpInterceptor,
    private translateService: TranslateService,
    private warningDialogService: WarningDialogService,
    private diskSpaceService: DiskSpaceService,
    private leadSheetService: LeadSheetService,
    private settingsService: SettingsService
  ) {
    this.dropzoneConfig = {
      url: this.appHttpInterceptor.getRestUrl() + 'lead-sheet/upload',
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

    this.loadLeadSheets();
    this.loadDiskSpace();
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  private loadLeadSheets() {
    this.leadSheetService.getLeadSheets().pipe(map(result => {
      this.existingLeadSheets = result;
      this.filterExistingLeadSheets();
    })).subscribe();
  }

  ngOnInit() {
    this.onClose = new Subject();

    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  private loadDiskSpace() {
    this.diskSpaceService.getDiskSpace().pipe(map(diskSpace => {
      this.diskSpaceUsedGB = Math.round(diskSpace.usedMB / 10) / 100;
      this.diskSpaceAvailableGB = Math.round(diskSpace.availableMB / 10) / 100;

      if(diskSpace.usedMB != 0) {
        this.diskSpacePercentage = Math.round(diskSpace.availableMB / diskSpace.usedMB);
      }
    })).subscribe();
  }

  public onUploadError(args: any) {
    console.log('Upload error', args);
  }

  public onUploadSuccess(args: any) {
    // Hide the preview element
    args[0].previewElement.hidden = true;

    // Select this file
    let instrumentUuuid;
    if (this.leadSheet) {
      instrumentUuuid = this.leadSheet.instrumentUuid;
    }

    this.leadSheet = new LeadSheet(args[1]);
    this.leadSheet.instrumentUuid = instrumentUuuid;
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }

  // Filter the existing lead sheets
  filterExistingLeadSheets(searchValue?: string) {
    if (!searchValue) {
      this.filteredExistingLeadSheets = this.existingLeadSheets;
      return;
    }

    this.filteredExistingLeadSheets = [];

    for (let leadSheet of this.existingLeadSheets) {
      if (leadSheet.name.toLowerCase().indexOf(searchValue.toLowerCase()) !== -1) {
        this.filteredExistingLeadSheets.push(leadSheet);
      }
    }
  }

  selectExistingLeadSheet(existingLeadSheet: LeadSheet) {
    if (this.leadSheet && this.leadSheet.name == existingLeadSheet.name) {
      // This lead sheet is already selected
      return;
    }

    this.leadSheet = existingLeadSheet;
  }

  deleteLeadSheet(existingLeadSheet: LeadSheet) {
    this.warningDialogService.show('editor.warning-delete-file').pipe(map(result => {
      if (result) {
        this.leadSheetService.deleteLeadSheet(existingLeadSheet).subscribe(() => {
          this.loadLeadSheets();
          this.loadDiskSpace();
        });
      }
    })).subscribe();
  }

}
