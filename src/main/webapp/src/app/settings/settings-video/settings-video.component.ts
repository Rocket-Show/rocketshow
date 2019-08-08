import { SettingsService } from './../../services/settings.service';
import { Component, OnInit } from '@angular/core';
import { Settings } from '../../models/settings';
import { map } from "rxjs/operators";

@Component({
  selector: 'app-settings-video',
  templateUrl: './settings-video.component.html',
  styleUrls: ['./settings-video.component.scss']
})
export class SettingsVideoComponent implements OnInit {

  selectUndefinedOptionValue: any = undefined;

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

  getResolution(): string {
    return undefined;
  }

  setResolution(resolution: string) {
    
  }

}
