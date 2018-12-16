import { SettingsService } from './../../services/settings.service';
import { SettingsPersonal } from './../../models/settings-personal';
import { SettingsPersonalService } from './../../services/settings-personal.service';
import { Component, OnInit } from '@angular/core';
import { Settings } from '../../models/settings';
import { map } from "rxjs/operators";

@Component({
  selector: 'app-settings-personal',
  templateUrl: './settings-personal.component.html',
  styleUrls: ['./settings-personal.component.scss']
})
export class SettingsPersonalComponent implements OnInit {

  selectUndefinedOptionValue: any;

  settings: Settings;
  settingsPersonal: SettingsPersonal;

  instrumentUuid: string;

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  private loadSettingsPersonal() {
    this.settingsPersonal = this.settingsPersonalService.getSettings();
  }

  constructor(
    private settingsPersonalService: SettingsPersonalService,
    private settingsService: SettingsService
  ) { }

  ngOnInit() {
    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });

    this.loadSettingsPersonal();

    this.settingsPersonalService.settingsChanged.subscribe(() => {
      this.loadSettingsPersonal();
    });
  }

}
