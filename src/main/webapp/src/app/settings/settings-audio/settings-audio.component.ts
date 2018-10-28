import { AudioDevice } from './../../models/audio-device';
import { Settings } from './../../models/settings';
import { SettingsService } from './../../services/settings.service';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-settings-audio',
  templateUrl: './settings-audio.component.html',
  styleUrls: ['./settings-audio.component.scss']
})
export class SettingsAudioComponent implements OnInit {

  settings: Settings;
  audioDeviceList: AudioDevice[];
  audioOutputList: string[] = [];

  constructor(
    private settingsService: SettingsService
  ) {
    this.audioOutputList.push('HEADPHONES');
    this.audioOutputList.push('HDMI');
    this.audioOutputList.push('DEVICE');
  }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;

      this.settingsService.getAudioDevices().subscribe((response) => {
        this.audioDeviceList = response;
      });
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  addAudioBus() {
    this.settingsService.addAudioBus(this.settings).subscribe();
  }

  deleteAudioBus(audioBusIndex: number) {
    this.settings.audioBusList.splice(audioBusIndex, 1);
  }

}
