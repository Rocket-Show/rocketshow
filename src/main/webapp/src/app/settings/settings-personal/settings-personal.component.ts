import { SettingsService } from './../../services/settings.service';
import { SettingsPersonal } from './../../models/settings-personal';
import { SettingsPersonalService } from './../../services/settings-personal.service';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { Settings } from '../../models/settings';
import { map } from "rxjs/operators";
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-settings-personal',
  templateUrl: './settings-personal.component.html',
  styleUrls: ['./settings-personal.component.scss']
})
export class SettingsPersonalComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;
  private settingsPersonalChangedSubscription: Subscription;

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

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });

    this.loadSettingsPersonal();

    this.settingsPersonalChangedSubscription = this.settingsPersonalService.settingsChanged.subscribe(() => {
      this.loadSettingsPersonal();
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
    this.settingsPersonalChangedSubscription.unsubscribe();
  }

}
