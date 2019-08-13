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
    if(this.settings.customVideoResolution) {
      return 'custom';
    }

    if(this.settings.videoWidth && this.settings.videoHeight) {
      return this.settings.videoWidth + ',' + this.settings.videoHeight;
    }

    return undefined;
  }

  setResolution(resolution: string) {
    if(!resolution) {
      this.settings.customVideoResolution = false;
      this.settings.videoWidth = undefined;
      this.settings.videoHeight = undefined;
      return;
    }

    if(resolution == 'custom') {
      this.settings.customVideoResolution = true;
    } else {
      this.settings.customVideoResolution = false;
      let dimensions: string[] = resolution.split(',');
      this.settings.videoWidth = Number.parseInt(dimensions[0]);
      this.settings.videoHeight = Number.parseInt(dimensions[1]);
    }
  }

}
