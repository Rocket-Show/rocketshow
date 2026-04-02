import { MidiDevice } from "./../models/midi-device";
import { AudioDevice } from "./../models/audio-device";
import { SettingsPersonalService } from "./../services/settings-personal.service";
import { ToastGeneralErrorService } from "./../services/toast-general-error.service";
import { Component, OnInit } from "@angular/core";
import { EMPTY, Observable, forkJoin } from "rxjs";
import {
  map,
  catchError,
  finalize,
  mergeMap,
  tap,
  switchMap,
} from "rxjs/operators";
import { Settings } from "../models/settings";
import { SettingsService } from "../services/settings.service";
import { PendingChangesDialogService } from "../services/pending-changes-dialog.service";
import { TranslateService } from "@ngx-translate/core";
import { ToastrService } from "ngx-toastr";

@Component({
  selector: "app-settings",
  templateUrl: "./settings.component.html",
  styleUrls: ["./settings.component.scss"],
  standalone: false
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

  private audioDeviceAvailable(
    audioDevice: AudioDevice,
    audioDeviceList: AudioDevice[]
  ): boolean {
    for (let existingAudioDevice of audioDeviceList) {
      if (
        existingAudioDevice.key == audioDevice.key &&
        existingAudioDevice.name == audioDevice.name
      ) {
        return true;
      }
    }

    return false;
  }

  private midiDeviceAvailable(
    midiDevice: MidiDevice,
    midiDeviceList: MidiDevice[]
  ): boolean {
    for (let existingMidiDevice of midiDeviceList) {
      if (
        existingMidiDevice.name == midiDevice.name &&
        existingMidiDevice.vendor == midiDevice.vendor
      ) {
        return true;
      }
    }

    return false;
  }

  ngOnInit(): void {
    this.settingsService
      .getSettings(true)
      .pipe(
        switchMap((result) =>
          forkJoin({
            midiInDevices: this.settingsService.getMidiInDevices(),
            midiOutDevices: this.settingsService.getMidiOutDevices(),
            audioDevices: this.settingsService.getAudioDevices(),
          }).pipe(
            switchMap(({ midiInDevices, midiOutDevices, audioDevices }) => {
              const noneDevice: MidiDevice = {
                id: -1,
                name: '[None]',
                vendor: '',
                description: '',
                serialPort: false,
              };

              if (!result.midiInDevice || result.midiInDevice.id === 0) {
                result.midiInDevice = noneDevice;
              }

              if (!result.midiOutDevice || result.midiOutDevice.id === 0) {
                result.midiOutDevice = noneDevice;
              }

              if (
                result.midiInDevice &&
                result.midiInDevice.id !== -1 &&
                !this.midiDeviceAvailable(result.midiInDevice, midiInDevices)
              ) {
                result.midiInDevice = midiInDevices.length > 0 ? midiInDevices[0] : undefined;
              }

              if (
                result.midiOutDevice &&
                result.midiOutDevice.id !== -1 &&
                !this.midiDeviceAvailable(result.midiOutDevice, midiOutDevices)
              ) {
                result.midiOutDevice = midiOutDevices.length > 0 ? midiOutDevices[0] : undefined;
              }

              if (result.audioBusList.length === 0) {
                return this.settingsService.addAudioBus(result).pipe(
                  tap(() => this.finishInit(result))
                );
              }

              this.finishInit(result);
              return EMPTY;
            })
          )
        )
      )
      .subscribe();
  }

  private copyInitialSettings(settings: Settings) {
    this.initialSettings = JSON.parse(JSON.stringify(settings));
  }

  canDeactivate(): Observable<boolean> {
    // const subject = new Subject<boolean>();

    // setTimeout(() => {
    //   subject.next(false); // Emit false after 3 seconds (prevent navigation)
    //   subject.complete(); // Complete the subject
    // }, 3000);

    // return subject.asObservable(); // Return as Observable so Angular waits

    // return of(true);
    // Does not work. In case false is returned, the component gets reloaded and the changes are lost anyway.
    return this.settingsService.getSettings().pipe(
      mergeMap((result) => {
        return this.pendingChangesDialogService.check(
          this.initialSettings,
          result,
          "settings.warning-settings-changes"
        );
      })
    );
  }

  discard() {
    this.settingsPersonalService.getSettings(true);

    this.settingsService
      .getSettings(true)
      .pipe(
        map((result) => {
          this.copyInitialSettings(result);
          this.settingsService.settingsChanged.next();

          this.translateService
            .get([
              "settings.toast-discard-success",
              "settings.toast-discard-success-title",
            ])
            .subscribe((result) => {
              this.toastrService.success(
                result["settings.toast-discard-success"],
                result["settings.toast-discard-success-title"]
              );
            });
        })
      )
      .subscribe();
  }

  private settingsError(errorKey: string) {
    this.translateService.get(errorKey).subscribe((result) => {
      this.toastrService.error(result);
    });

    this.savingSettings = false;
  }

  save() {
    this.savingSettings = true;

    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.translateService
            .get([
              "intro.default-unit-name",
              "settings.remote-device-name-placeholder",
            ])
            .subscribe((translations) => {
              // Save the personal settings
              this.settingsPersonalService.save(
                this.settingsPersonalService.getSettings()
              );

              // Set some default settings
              if (!result.deviceName) {
                result.deviceName = translations["intro.default-unit-name"];
              }

              for (let i = 0; i < result.remoteDeviceList.length; i++) {
                let remoteDevice = result.remoteDeviceList[i];

                if (!remoteDevice.name) {
                  remoteDevice.name =
                    translations["settings.remote-device-name-placeholder"] +
                    " " +
                    (i + 1);
                }

                if (!remoteDevice.host) {
                  remoteDevice.host = "192.168.1.22";
                }
              }

              if (!result.wlanApSsid || result.wlanApSsid.length == 0) {
                result.wlanApSsid = "Rocket Show";
              }

              if (
                result.wlanApPassphrase &&
                result.wlanApPassphrase.length < 8 &&
                result.wlanApPassphrase.length > 0
              ) {
                this.settingsError(
                  "settings.wlan-ap-wpa-passphrase-short-error"
                );
                return;
              }

              // Save the settings on the device
              this.settingsService
                .saveSettings()
                .pipe(
                  map(() => {
                    // http -> https or vice versa
                    const protocolChanged = this.initialSettings.tlsEnable != result.tlsEnable;

                    this.copyInitialSettings(result);
                    this.translateService.use(result.language);

                    this.settingsService.settingsChanged.next();
                    this.settingsPersonalService.settingsChanged.next();

                    if (protocolChanged) {
                      const host = window.location.hostname;
                      const hash = '/#/settings?settingsSaved=true';

                      const targetUrl = result.tlsEnable
                        ? `https://${host}${hash}`
                        : `http://${host}${hash}`;

                      window.location.replace(targetUrl);
                      // window.location.reload();
                    } else {
                      this.translateService
                        .get([
                          "settings.toast-save-success",
                          "settings.toast-save-success-title",
                        ])
                        .subscribe((result) => {
                          this.toastrService.success(
                            result["settings.toast-save-success"],
                            result["settings.toast-save-success-title"]
                          );
                        });
                    }
                  }),
                  catchError((err) => {
                    return this.toastGeneralErrorService.show(err);
                  }),
                  finalize(() => {
                    this.savingSettings = false;
                  })
                )
                .subscribe();
            });
        })
      )
      .subscribe();
  }
}
