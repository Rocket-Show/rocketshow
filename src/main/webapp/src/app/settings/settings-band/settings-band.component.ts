import { UuidService } from './../../services/uuid.service';
import { Instrument } from './../../models/instrument';
import { SettingsService } from './../../services/settings.service';
import { Settings } from './../../models/settings';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-settings-band',
  templateUrl: './settings-band.component.html',
  styleUrls: ['./settings-band.component.scss']
})
export class SettingsBandComponent implements OnInit {

  settings: Settings;

  constructor(
    private settingsService: SettingsService,
    private uuidService: UuidService
  ) { }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  addInstrument() {
    let instrument: Instrument = new Instrument();
    instrument.uuid = this.uuidService.getUuid();
    this.settings.instrumentList.push(instrument);
  }

  deleteInstrument(instrumentIndex: number) {
    this.settings.instrumentList.splice(instrumentIndex, 1);
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

}
