import { AudioDevice } from './../../models/audio-device';
import { Settings } from './../../models/settings';
import { SettingsService } from './../../services/settings.service';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { OperatingSystemInformationService } from '../../services/operating-system-information.service';
import { map } from "rxjs/operators";
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-settings-audio',
  templateUrl: './settings-audio.component.html',
  styleUrls: ['./settings-audio.component.scss']
})
export class SettingsAudioComponent implements OnInit, OnDestroy {

  selectUndefinedOptionValue: any;

  private settingsChangedSubscription: Subscription;

  settings: Settings;
  audioDeviceList: AudioDevice[];
  audioOutputList: string[] = [];
  maxAudioChannels: number = 9999;
  currentAudioChannels: number = 0;

  constructor(
    private settingsService: SettingsService,
    private operatingSystemInformationService: OperatingSystemInformationService
  ) {
    this.operatingSystemInformationService.getOperatingSystemInformation().subscribe(operatingSystemInformation => {
      if(operatingSystemInformation.type == 'WINDOWS') {
        // TODO
      } else if(operatingSystemInformation.type == 'OS_X') {
        this.audioOutputList.push('DEFAULT');
      } else if(operatingSystemInformation.subType == 'RASPBERRYOS') {
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

      this.updateCurrentAudioChannels();
    })).subscribe();

    this.settingsService.getMaxAudioChannels().subscribe(channels => {
      this.maxAudioChannels = channels;
    });
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  private updateCurrentAudioChannels() {
    this.currentAudioChannels = 0;

    for(let audioBus of this.settings.audioBusList) {
      this.currentAudioChannels += audioBus.channels;
    }

    // Audiochannels may be prefixed with 0 if changed from option element.
    // -> Remove the 0
    let text: string = this.currentAudioChannels.toString();
    this.currentAudioChannels = Number.parseInt(text);
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  addAudioBus() {
    this.settingsService.addAudioBus(this.settings).subscribe(() => {
      this.updateCurrentAudioChannels();
    });
  }

  deleteAudioBus(audioBusIndex: number) {
    this.settings.audioBusList.splice(audioBusIndex, 1);
    this.updateCurrentAudioChannels();
  }

}
