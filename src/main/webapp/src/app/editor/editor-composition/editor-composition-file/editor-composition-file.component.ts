import { DiskSpaceService } from './../../../services/disk-space.service';
import { AppHttpInterceptor } from './../../../app-http-interceptor/app-http-interceptor';
import { Settings } from './../../../models/settings';
import { SettingsService } from './../../../services/settings.service';
import { WarningDialogService } from './../../../services/warning-dialog.service';
import { CompositionMidiFile } from './../../../models/composition-midi-file';
import { FileService } from './../../../services/file.service';
import { TranslateService } from '@ngx-translate/core';
import { CompositionFile } from './../../../models/composition-file';
import { Component, OnInit } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap';
import { Subject } from 'rxjs';
import { map } from "rxjs/operators";
import { Composition } from '../../../models/composition';
import { DropzoneConfigInterface } from 'ngx-dropzone-wrapper/dist/lib/dropzone.interfaces';
import { CompositionAudioFile } from '../../../models/composition-audio-file';
import { AudioBus } from '../../../models/audio-bus';

@Component({
  selector: 'app-editor-composition-file',
  templateUrl: './editor-composition-file.component.html',
  styleUrls: ['./editor-composition-file.component.scss'],
})
export class EditorCompositionFileComponent implements OnInit {

  selectUndefinedOptionValue: any = undefined;

  fileIndex: number;
  file: CompositionFile;
  composition: Composition;
  settings: Settings;

  onClose: Subject<number>;

  existingFiles: CompositionFile[] = [];
  filteredExistingFiles: CompositionFile[] = [];

  dropzoneConfig: DropzoneConfigInterface;
  uploadMessage: string;

  diskSpaceUsedGB: number = 0;
  diskSpaceAvailableGB: number = 0;
  diskSpacePercentage: number = 0;

  constructor(
    private bsModalRef: BsModalRef,
    private appHttpInterceptor: AppHttpInterceptor,
    private translateService: TranslateService,
    private fileService: FileService,
    private warningDialogService: WarningDialogService,
    private settingsService: SettingsService,
    private diskSpaceService: DiskSpaceService) {

    this.dropzoneConfig = {
      url: this.appHttpInterceptor.getRestUrl() + 'file/upload',
      addRemoveLinks: false,
      maxFilesize: 10000 /* 10 GB */,
      acceptedFiles: 'audio/*,video/*',
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

    this.loadFiles();
    this.loadDiskSpace();
  }

  private loadDiskSpace() {
    this.diskSpaceService.getDiskSpace().pipe(map(diskSpace => {
      this.diskSpaceUsedGB = diskSpace.usedMB / 1024;
      this.diskSpaceAvailableGB = (diskSpace.availableMB + diskSpace.availableMB) / 1024;

      this.diskSpaceUsedGB = Math.round(this.diskSpaceUsedGB * 100) / 100;
      this.diskSpaceAvailableGB = Math.round(this.diskSpaceAvailableGB * 100) / 100;

      if (diskSpace.usedMB != 0) {
        this.diskSpacePercentage = Math.round(100 * diskSpace.usedMB / (diskSpace.usedMB + diskSpace.availableMB));
      }
    })).subscribe();
  }

  private audioBusListContainsBus(audioBusList: AudioBus[], name: string): boolean {
    for (let audioBus of audioBusList) {
      if (audioBus.name == name) {
        return true;
      }
    }

    return false;
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;

      // Set the default bus, if the chosen one is not available anymore (e.g. due to changed settings)
      if (this.file && this.file instanceof CompositionAudioFile) {
        let compositionAudioFile = <CompositionAudioFile>this.file;

        if (compositionAudioFile.outputBus && this.settings && this.settings.audioBusList) {
          if (!this.audioBusListContainsBus(this.settings.audioBusList, compositionAudioFile.outputBus)) {
            compositionAudioFile.outputBus = this.settings.audioBusList[0].name;
          }
        }
      }
    })).subscribe();
  }

  private loadFiles() {
    this.fileService.getFiles().pipe(map(result => {
      this.existingFiles = result;
      this.filterExistingFiles();
    })).subscribe();
  }

  ngOnInit() {
    this.onClose = new Subject();

    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }

  public onUploadError(args: any) {
    console.log('Upload error', args);
  }

  private setDefaultOutputBus(compositionAudioFile: CompositionAudioFile) {
    if (this.settings && this.settings.audioBusList) {
      compositionAudioFile.outputBus = this.settings.audioBusList[0].name;
    }
  }

  public onUploadSuccess(args: any) {
    this.loadFiles();
    this.loadDiskSpace();

    // Hide the preview element
    args[0].previewElement.hidden = true;

    // Select this file
    let midiRoutingList;
    if (this.file && this.file instanceof CompositionMidiFile) {
      midiRoutingList = (<CompositionMidiFile>this.file).midiRoutingList;
    }

    this.file = Composition.getFileObjectByType(args[1]);

    if (this.file && this.file instanceof CompositionAudioFile) {
      this.setDefaultOutputBus((<CompositionAudioFile>this.file));
    }

    if (this.file instanceof CompositionMidiFile && midiRoutingList) {
      (<CompositionMidiFile>this.file).midiRoutingList = midiRoutingList;
    }
  }

  // Filter the existing files
  filterExistingFiles(searchValue?: string) {
    if (!searchValue) {
      this.filteredExistingFiles = this.existingFiles;
      return;
    }

    this.filteredExistingFiles = [];

    for (let file of this.existingFiles) {
      if (file.name.toLowerCase().indexOf(searchValue.toLowerCase()) !== -1) {
        this.filteredExistingFiles.push(file);
      }
    }
  }

  selectExistingFile(existingFile: CompositionFile) {
    if (this.file.name == existingFile.name && this.file.type == existingFile.type) {
      // This file is already selected
      return;
    }

    let midiRoutingList;
    if (this.file && this.file instanceof CompositionMidiFile) {
      midiRoutingList = (<CompositionMidiFile>this.file).midiRoutingList;
    }

    this.file = existingFile;

    this.setDefaultOutputBus((<CompositionAudioFile>this.file));

    if (this.file instanceof CompositionMidiFile && midiRoutingList) {
      (<CompositionMidiFile>this.file).midiRoutingList = midiRoutingList;
    }
  }

  deleteFile(existingFile: CompositionFile) {
    this.warningDialogService.show('editor.warning-delete-file').pipe(map(result => {
      if (result) {
        this.fileService.deleteFile(existingFile).subscribe(() => {
          this.loadFiles();
          this.loadDiskSpace();
        });
      }
    })).subscribe();
  }

}
