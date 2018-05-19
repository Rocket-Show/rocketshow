import { ToastGeneralErrorService } from './../services/toast-general-error.service';
import { Component, OnInit, Directive, Output, EventEmitter } from '@angular/core';
import { Observable } from 'rxjs/Observable';
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
    private pendingChangesDialogService: PendingChangesDialogService,
    private translateService: TranslateService,
    private toastrService: ToastrService,
    private toastGeneralErrorService: ToastGeneralErrorService
  ) { }

  private finishInit(settings: Settings) {
    this.copyInitialSettings(settings);
    this.loading = false;
  }

  ngOnInit() {
    this.settingsService.getSettings(true).map(result => {
      // Set the initial devices
      Observable.forkJoin(
        this.settingsService.getMidiInDevices(),
        this.settingsService.getMidiOutDevices(),
        this.settingsService.getAudioDevices()
      ).subscribe((devices) => {
        if((!result.midiInDevice || result.midiInDevice && result.midiInDevice.id == 0) && devices[0].length > 0) {
          result.midiInDevice = devices[0][0];
        }

        if((!result.midiOutDevice || result.midiOutDevice && result.midiOutDevice.id == 0) && devices[1].length > 0) {
          result.midiOutDevice = devices[1][0];
        }

        if((!result.audioDevice || result.audioDevice && result.audioDevice.id == 0) && devices[2].length > 0) {
          result.audioDevice = devices[2][0];
        }

        if(result.audioBusList.length == 0) {
          // Add a default bus
          this.settingsService.addAudioBus(result).subscribe(() => {
            this.finishInit(result);
          });
        } else {
          this.finishInit(result);
        }
      });
    }).subscribe();
  }

  private copyInitialSettings(settings: Settings) {
    this.initialSettings = JSON.parse(JSON.stringify(settings));
  }

  canDeactivate(): Observable<boolean> {
    return this.settingsService.getSettings().flatMap(result => {
      return this.pendingChangesDialogService.check(this.initialSettings, result, 'settings.warning-settings-changes');
    });
  }

  discard() {
    this.settingsService.getSettings(true).map(result => {
      this.copyInitialSettings(result);
      this.settingsService.settingsChanged.next();

      this.translateService.get(['settings.toast-discard-success', 'settings.toast-discard-success-title']).subscribe(result => {
        this.toastrService.success(result['settings.toast-discard-success'], result['settings.toast-discard-success-title']);
      });
    }).subscribe();
  }

  private settingsError(errorKey: string) {
    this.translateService.get(errorKey).subscribe(result => {
      this.toastrService.error(result);
    });

    this.savingSettings = false;
  }

  save() {
    this.savingSettings = true;

    this.settingsService.getSettings().map(result => {
      this.translateService.get(['intro.default-unit-name', 'settings.remote-device-name-placeholder']).subscribe((translations) => {
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
        this.settingsService.saveSettings(result).map(() => {
          this.copyInitialSettings(result);
          this.translateService.use(result.language);

          this.settingsService.settingsChanged.next();

          this.translateService.get(['settings.toast-save-success', 'settings.toast-save-success-title']).subscribe(result => {
            this.toastrService.success(result['settings.toast-save-success'], result['settings.toast-save-success-title']);
          });
        })
          .catch((err) =>  {
            return this.toastGeneralErrorService.show(err);
          })
          .finally(() => {
            this.savingSettings = false;
          })
          .subscribe();
      });
    }).subscribe();
  }
  
}
