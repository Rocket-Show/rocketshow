import { HttpClient } from '@angular/common/http';
import { UpdateDialogComponent } from './../../update-dialog/update-dialog.component';
import { BsModalService } from 'ngx-bootstrap/modal';
import { UpdateService } from './../../services/update.service';
import { WarningDialogService } from './../../services/warning-dialog.service';
import { Settings } from './../../models/settings';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { SettingsService } from '../../services/settings.service';
import { CompositionService } from '../../services/composition.service';
import { Composition } from '../../models/composition';
import { Version } from '../../models/version';
import { map } from 'rxjs/operators';
import { OperatingSystemInformation } from '../../models/operating-system-information';
import { OperatingSystemInformationService } from '../../services/operating-system-information.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-settings-system',
  templateUrl: './settings-system.component.html',
  styleUrls: ['./settings-system.component.scss']
})
export class SettingsSystemComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;

  selectUndefinedOptionValue: any = undefined;

  settings: Settings;
  compositions: Composition[];
  currentVersion: Version;
  operatingSystemInformation: OperatingSystemInformation;

  constructor(
    public settingsService: SettingsService,
    private warningDialogService: WarningDialogService,
    private http: HttpClient,
    private compositionService: CompositionService,
    private updateService: UpdateService,
    private modalService: BsModalService,
    private operatingSystemInformationService: OperatingSystemInformationService) {

    this.operatingSystemInformationService.getOperatingSystemInformation().subscribe(operatingSystemInformation => {
      this.operatingSystemInformation = operatingSystemInformation;
    });
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });

    this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
      this.compositions = compositions;
    });

    this.updateService.getCurrentVersion().subscribe((version: Version) => {
      this.currentVersion = version;
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  switchLanguage(language: string) {
    this.settings.language = language;
  }

  reboot() {
    this.warningDialogService.show('settings.warning-reboot').pipe(map(result => {
      if (result) {
        this.http.post('system/reboot', undefined).subscribe();
      }
    })).subscribe();
  }

  shutdown() {
    this.warningDialogService.show('settings.warning-shutdown').pipe(map(result => {
      if (result) {
        this.http.post('system/shutdown', undefined).subscribe();
      }
    })).subscribe();
  }

  checkVersion() {
    // Show the file details dialog
    let updateDialog = this.modalService.show(UpdateDialogComponent, { keyboard: false, ignoreBackdropClick: true, class: '' });
  }

}
