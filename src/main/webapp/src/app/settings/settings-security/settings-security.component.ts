import { Component, OnDestroy, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { SettingsService } from '../../services/settings.service';
import { Settings } from '../../models/settings';
import { Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { BsModalService } from 'ngx-bootstrap/modal';
import { NewApiKeyDialogComponent } from '../new-api-key-dialog/new-api-key-dialog.component';
import { ChangePasswordDialogComponent } from '../change-password-dialog/change-password-dialog.component';
import { DeviceInformationService } from '../../services/device-information.service';
import { DeviceInformation } from '../../models/device-information';

@Component({
  selector: 'app-settings-security',
  templateUrl: './settings-security.component.html',
  styleUrl: './settings-security.component.scss',
  standalone: false
})
export class SettingsSecurityComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;
  settings: Settings;
  deviceInformation: DeviceInformation;

  constructor(
    public authService: AuthService,
    private settingsService: SettingsService,
    private modalService: BsModalService,
    private deviceInformationService: DeviceInformationService,
  ) {
    this.deviceInformationService.getDeviceInformation().subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  public logout() {
    this.authService.logout().subscribe({
      next: () => {
        this.authService.init().subscribe();
      },
      error: (err) => { }
    });
  }

  public changePassword() {
    this.modalService.show(ChangePasswordDialogComponent, {
      keyboard: false,
      ignoreBackdropClick: true,
      class: "",
    });
  }

  public addApiKey() {
    let newApiKeyDialog = this.modalService.show(NewApiKeyDialogComponent, {
      keyboard: false,
      ignoreBackdropClick: true,
      class: "modal-lg",
    }).content;;

    newApiKeyDialog.onClose.subscribe((result) => {
      if (result === 1) {
        // Add the new API key
        this.settings.apiKeyList.push(newApiKeyDialog.apiKey);
      }
    })
  }

  public deleteApiKey(index: number) {
    this.settings.apiKeyList.splice(index, 1);
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

}
