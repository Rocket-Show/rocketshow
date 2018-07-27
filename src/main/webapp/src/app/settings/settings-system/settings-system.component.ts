import { HttpClient } from '@angular/common/http';
import { UpdateDialogComponent } from './../../update-dialog/update-dialog.component';
import { BsModalService } from 'ngx-bootstrap/modal';
import { UpdateService } from './../../services/update.service';
import { WarningDialogService } from './../../services/warning-dialog.service';
import { Settings } from './../../models/settings';
import { Component, OnInit } from '@angular/core';
import { SettingsService } from '../../services/settings.service';
import { CompositionService } from '../../services/composition.service';
import { Composition } from '../../models/composition';
import { Version } from '../../models/version';

@Component({
  selector: 'app-settings-system',
  templateUrl: './settings-system.component.html',
  styleUrls: ['./settings-system.component.scss']
})
export class SettingsSystemComponent implements OnInit {

  selectUndefinedOptionValue: any;

  settings: Settings;
  compositions: Composition[];
  currentVersion: Version;

  constructor(
    public settingsService: SettingsService,
    private warningDialogService: WarningDialogService,
    private http: HttpClient,
    private compositionService: CompositionService,
    private updateService: UpdateService,
    private modalService: BsModalService) { }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });

    this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
      this.compositions = compositions;
    });

    this.updateService.getCurrentVersion().subscribe((version: Version) => {
      this.currentVersion = version;
    });
  }

  switchLanguage(language: string) {
    this.settings.language = language;
  }

  reboot() {
    this.warningDialogService.show('settings.warning-reboot').map(result => {
      if (result) {
        this.http.post('system/reboot', undefined).subscribe();
      }
    }).subscribe();
  }

  checkVersion() {
    // Show the file details dialog
    let updateDialog = this.modalService.show(UpdateDialogComponent, { keyboard: false, ignoreBackdropClick: true, class: '' });
  }

}
