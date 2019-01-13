import { AudioDevice } from './../../models/audio-device';
import { Settings } from './../../models/settings';
import { SettingsService } from './../../services/settings.service';
import { Component, OnInit } from '@angular/core';
import { OperatingSystemInformationService } from '../../services/operating-system-information.service';
import { map } from "rxjs/operators";

@Component({
  selector: 'app-settings-audio',
  templateUrl: './settings-audio.component.html',
  styleUrls: ['./settings-audio.component.scss']
})
export class SettingsAudioComponent implements OnInit {

  selectUndefinedOptionValue: any;

  settings: Settings;
  audioDeviceList: AudioDevice[];
  audioOutputList: string[] = [];
  
  constructor(
    private settingsService: SettingsService,
    private operatingSystemInformationService: OperatingSystemInformationService
  ) {
    this.operatingSystemInformationService.getOperatingSystemInformation().subscribe(operatingSystemInformation => {
      if(operatingSystemInformation.type == 'WINDOWS') {
        // TODO
      } else if(operatingSystemInformation.type == 'OS_X') {
        this.audioOutputList.push('DEFAULT');
      } else if(operatingSystemInformation.subType == 'RASPBIAN') {
        // Currently disabled
        //this.audioOutputList.push('HEADPHONES');
        //this.audioOutputList.push('HDMI');
        this.audioOutputList.push('DEVICE');
      } else if(operatingSystemInformation.type == 'LINUX') {
        this.audioOutputList.push('DEVICE');
      }
    });
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;

      this.settingsService.getAudioDevices().subscribe((response) => {
        this.audioDeviceList = response;

        for(let audioDevice of this.audioDeviceList) {
          if(this.settings.audioDevice && audioDevice.key == this.settings.audioDevice.key) {
            this.settings.audioDevice = audioDevice;
          }
        }
      });
    })).subscribe();
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
