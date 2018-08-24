import { UuidService } from './../services/uuid.service';
import { SettingsService } from './../services/settings.service';
import { Settings } from './../models/settings';
import { RemoteDevice } from './../models/remote-device';
import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-remote-device-selection',
  templateUrl: './remote-device-selection.component.html',
  styleUrls: ['./remote-device-selection.component.scss']
})
export class RemoteDeviceSelectionComponent implements OnInit {
  @Input() remoteDeviceList: string[];

  settings: Settings;

  // Used to generate unique ids for the checkboxes inside this component,
  // if this component is used multiple times in the same page
  uuid: string;

  constructor(
    private settingsService: SettingsService,
    private uuidService: UuidService
  ) {
    this.uuid = uuidService.getUuid();
  }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();
  }

  remoteDeviceChecked(remoteDevice: RemoteDevice) {
    if (!this.remoteDeviceList) {
      return;
    }

    for (let midiControlRemoteDevice of this.remoteDeviceList) {
      if (midiControlRemoteDevice == remoteDevice.name) {
        return true;
      }
    }

    return false;
  }

  updateRemoteDeviceSync(remoteDevice: RemoteDevice, value: boolean) {
    if (!this.remoteDeviceList) {
      return;
    }

    if (value) {
      // Add the remote device, if not already added
      for (let existingRemoteDevice of this.remoteDeviceList) {
        if (existingRemoteDevice == remoteDevice.name) {
          // Already added -> nothing to do
          return;
        }
      }

      // Add the new remote device
      this.remoteDeviceList.push(remoteDevice.name);
    } else {
      // Remove the remote device, if not already removed
      for (let i = 0; i < this.remoteDeviceList.length; i++) {
        if (this.remoteDeviceList[i] == remoteDevice.name) {
          this.remoteDeviceList.splice(i, 1);
          return;
        }
      }
    }
  }

}
