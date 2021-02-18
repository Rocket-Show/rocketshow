import { MidiRouting } from './../models/midi-routing';
import { Component, OnInit } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { Subject } from 'rxjs';
import { RemoteDevice } from '../models/remote-device';
import { SettingsService } from '../services/settings.service';

@Component({
  selector: 'app-routing-details',
  templateUrl: './routing-details.component.html',
  styleUrls: ['./routing-details.component.scss'],
})
export class RoutingDetailsComponent implements OnInit {

  midiRouting: MidiRouting;
  onClose: Subject<number>;
  remoteDevices: RemoteDevice[];

  midiDestinationList: string[] = [];

  constructor(
    private bsModalRef: BsModalRef,
    private settingsService: SettingsService) {

    this.midiDestinationList.push('OUT_DEVICE');
    this.midiDestinationList.push('LIGHTING');
    this.midiDestinationList.push('REMOTE');
  }

  ngOnInit() {
    this.onClose = new Subject();

    this.settingsService.getSettings().subscribe((result) => {
      this.remoteDevices = result.remoteDeviceList;
    });
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }

}
