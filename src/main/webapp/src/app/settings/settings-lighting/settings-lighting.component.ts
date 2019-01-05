import { Settings } from '../../models/settings';
import { SettingsService } from '../../services/settings.service';
import { Component, OnInit } from '@angular/core';
import { map } from "rxjs/operators";

@Component({
  selector: 'app-settings-lighting',
  templateUrl: './settings-lighting.component.html',
  styleUrls: ['./settings-lighting.component.scss']
})
export class SettingsLightingComponent implements OnInit {

  settings: Settings;

  constructor(
    private settingsService: SettingsService,
  ) { }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  ngOnInit() {
    this.loadSettings();
  }

}
