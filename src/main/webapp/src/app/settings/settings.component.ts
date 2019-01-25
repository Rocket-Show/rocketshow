import { MidiDevice } from './../models/midi-device';
import { AudioDevice } from './../models/audio-device';
import { SettingsPersonalService } from './../services/settings-personal.service';
import { ToastGeneralErrorService } from './../services/toast-general-error.service';
import { Component, OnInit } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { map, flatMap, catchError, finalize } from 'rxjs/operators';
import { Settings } from '../models/settings';
import { SettingsService } from '../services/settings.service';
import { PendingChangesDialogService } from '../services/pending-changes-dialog.service';
import { TranslateService } from '@ngx-translate/core';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  // The settings as they were, when we loaded them
  initialSettings: Settings;

  loading: boolean = true;

  savingSettings: boolean = false;

  constructor(
    private settingsService: SettingsService,
    private settingsPersonalService: SettingsPersonalService,
    private pendingChangesDialogService: PendingChangesDialogService,
    private translateService: TranslateService,
    private toastrService: ToastrService,
    private toastGeneralErrorService: ToastGeneralErrorService
  ) { }

  private finishInit(settings: Settings) {
    this.copyInitialSettings(settings);
    this.loading = false;
  }

  private audioDeviceAvailable(audioDevice: AudioDevice, audioDeviceList: AudioDevice[]): boolean {
    for(let existingAudioDevice of audioDeviceList) {
      if(existingAudioDevice.key == audioDevice.key && existingAudioDevice.name == audioDevice.name) {
        return true;
      }
    }

    return false;
  }

  private midiDeviceAvailable(midiDevice: MidiDevice, midiDeviceList: MidiDevice[]): boolean {
    for(let existingMidiDevice of midiDeviceList) {
      if(existingMidiDevice.name == midiDevice.name && existingMidiDevice.vendor == midiDevice.vendor) {
        return true;
      }
    }

    return false;
  }

  ngOnInit() {
    this.settingsService.getSettings(true).pipe(map(result => {
      forkJoin(
        this.settingsService.getMidiInDevices(),
        this.settingsService.getMidiOutDevices(),
        this.settingsService.getAudioDevices()
      ).pipe(map(data => {
        let midiInDevices = <MidiDevice[]>data[0];
        let midiOutDevices = <MidiDevice[]>data[1];
        let audioDevices = <AudioDevice[]>data[2];

        // Set the initial devices where none are set
        if((!result.midiInDevice || result.midiInDevice && result.midiInDevice.id == 0) && midiInDevices.length > 0) {
          result.midiInDevice = midiInDevices[0];
        }

        if((!result.midiOutDevice || result.midiOutDevice && result.midiOutDevice.id == 0) && midiOutDevices.length > 0) {
          result.midiOutDevice = midiOutDevices[0];
        }

        if((!result.audioDevice || result.audioDevice && result.audioDevice.id == 0) && audioDevices.length > 0) {
          result.audioDevice = audioDevices[0];
        }

        // Remove/change the devices, if they current ones are not available anymore
        if(result.midiInDevice && !this.midiDeviceAvailable(result.midiInDevice, midiInDevices)) {
          if(midiInDevices.length > 0) {
            result.midiInDevice = midiInDevices[0];
          } else {
            result.midiInDevice = undefined;
          }
        }

        if(result.midiOutDevice && !this.midiDeviceAvailable(result.midiOutDevice, midiOutDevices)) {
          if(midiOutDevices.length > 0) {
            result.midiOutDevice = midiOutDevices[0];
          } else {
            result.midiOutDevice = undefined;
          }
        }
        
        if(result.audioDevice && !this.audioDeviceAvailable(result.audioDevice, audioDevices)) {
          if(audioDevices.length > 0) {
            result.audioDevice = audioDevices[0];
          } else {
            result.audioDevice = undefined;
          }
        }

        if(result.audioBusList.length == 0) {
          // Add a default bus
          this.settingsService.addAudioBus(result).subscribe(() => {
            this.finishInit(result);
          });
        } else {
          this.finishInit(result);
        }
      })).subscribe();
    })).subscribe();
  }

  private copyInitialSettings(settings: Settings) {
    this.initialSettings = JSON.parse(JSON.stringify(settings));
  }

  canDeactivate(): Observable<boolean> {
    return this.settingsService.getSettings().pipe(flatMap(result => {
      return this.pendingChangesDialogService.check(this.initialSettings, result, 'settings.warning-settings-changes');
    }));
  }

  discard() {
    this.settingsPersonalService.getSettings(true);

    this.settingsService.getSettings(true).pipe(map(result => {
      this.copyInitialSettings(result);
      this.settingsService.settingsChanged.next();

      this.translateService.get(['settings.toast-discard-success', 'settings.toast-discard-success-title']).subscribe(result => {
        this.toastrService.success(result['settings.toast-discard-success'], result['settings.toast-discard-success-title']);
      });
    })).subscribe();
  }

  private settingsError(errorKey: string) {
    this.translateService.get(errorKey).subscribe(result => {
      this.toastrService.error(result);
    });

    this.savingSettings = false;
  }

  save() {
    this.savingSettings = true;

    this.settingsService.getSettings().pipe(map(result => {
      this.translateService.get(['intro.default-unit-name', 'settings.remote-device-name-placeholder']).subscribe((translations) => {
        // Save the personal settings
        this.settingsPersonalService.save(this.settingsPersonalService.getSettings());
        
        // Set some default settings
        if (!result.deviceName) {
          result.deviceName = translations['intro.default-unit-name'];
        }

        for (let i = 0; i < result.remoteDeviceList.length; i++) {
          let remoteDevice = result.remoteDeviceList[i];

          if (!remoteDevice.name) {
            remoteDevice.name = translations['settings.remote-device-name-placeholder'] + ' ' + (i + 1);
          }

          if (!remoteDevice.host) {
            remoteDevice.host = '192.168.1.22';
          }
        }

        if(!result.wlanApSsid || result.wlanApSsid.length == 0) {
          result.wlanApSsid = 'Rocket Show';
        }

        if(result.wlanApPassphrase && result.wlanApPassphrase.length < 8 && result.wlanApPassphrase.length > 0) {
          this.settingsError('settings.wlan-ap-wpa-passphrase-short-error');
          return;
        }

        // Save the settings on the device
        this.settingsService.saveSettings(result).pipe(map(() => {
          this.copyInitialSettings(result);
          this.translateService.use(result.language);

          this.settingsService.settingsChanged.next();

          this.translateService.get(['settings.toast-save-success', 'settings.toast-save-success-title']).subscribe(result => {
            this.toastrService.success(result['settings.toast-save-success'], result['settings.toast-save-success-title']);
          });
        }),
          catchError((err) =>  {
            return this.toastGeneralErrorService.show(err);
          })
          ,finalize(() => {
            this.savingSettings = false;
          }))
          .subscribe();
      });
    })).subscribe();
  }
  
}
