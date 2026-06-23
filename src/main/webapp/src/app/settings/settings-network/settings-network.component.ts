import { TranslateService } from '@ngx-translate/core';
import { RemoteDevice } from './../../models/remote-device';
import { Settings } from './../../models/settings';
import { LanInfo } from './../../models/lan-info';
import { SettingsService } from './../../services/settings.service';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { map } from "rxjs/operators";
import { Subscription } from 'rxjs';

@Component({
    selector: 'app-settings-network',
    templateUrl: './settings-network.component.html',
    styleUrls: ['./settings-network.component.scss'],
    standalone: false
})
export class SettingsNetworkComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;

  selectUndefinedOptionValue: any = undefined;

  settings: Settings;
  currentLanInfo: LanInfo = { ipAddress: '', subnetMask: '', gateway: '', dns1: '', dns2: '' };

  constructor(
    private settingsService: SettingsService,
    private translateService: TranslateService
  ) { }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  private loadCurrentLanInfo() {
    this.settingsService.getLanInfo().pipe(map(result => {
      this.currentLanInfo = result;
    })).subscribe();
  }

  onLanStaticIpEnableChange(enabled: boolean) {
    this.settings.lanStaticIpEnable = enabled;
    if (enabled) {
      this.settings.lanIpAddress = this.currentLanInfo.ipAddress;
      this.settings.lanSubnetMask = this.currentLanInfo.subnetMask;
      this.settings.lanGateway = this.currentLanInfo.gateway;
      this.settings.lanDns1 = this.currentLanInfo.dns1;
      this.settings.lanDns2 = this.currentLanInfo.dns2;
    }
  }

  ngOnInit() {
    this.loadSettings();
    this.loadCurrentLanInfo();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  addRemoteDevice() {
    this.translateService.get('settings.remote-device-name-placeholder').subscribe(result => {
      let remoteDevice: RemoteDevice = new RemoteDevice();
      remoteDevice.name = result + ' ' + (this.settings.remoteDeviceList.length + 1);
      this.settings.remoteDeviceList.push(remoteDevice);
    });
  }

  deleteRemoteDevice(remoteDeviceIndex: number) {
    this.settings.remoteDeviceList.splice(remoteDeviceIndex, 1);
  }

  updateChannel() {
    if (this.settings.wlanApHwMode == 'b' || this.settings.wlanApHwMode == 'g') {
      this.settings.wlanApChannel = 7;
    } else if (this.settings.wlanApHwMode == 'a') {
      this.settings.wlanApChannel = 44;
    } else if (this.settings.wlanApHwMode == 'ad') {
      this.settings.wlanApChannel = 5;
    }
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

}
