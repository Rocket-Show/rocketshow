import { Subject } from 'rxjs/Subject';
import { Injectable } from '@angular/core';
import { SettingsPersonal } from '../models/settings-personal';

@Injectable({
  providedIn: 'root'
})
export class SettingsPersonalService {

  // Fires, when the settings have changed
  settingsChanged: Subject<void> = new Subject<void>();

  private readonly settingsKey = 'settings';

  private settingsPersonal: SettingsPersonal;

  constructor() { }

  private readFromLocalStorage() {
    this.settingsPersonal = new SettingsPersonal(JSON.parse(localStorage.getItem(this.settingsKey)));
  }

  getSettings(clearCache: boolean = false): SettingsPersonal {
    if(clearCache) {
      this.readFromLocalStorage();
    }

    if(this.settingsPersonal) {
      return this.settingsPersonal;
    }

    this.readFromLocalStorage();

    return this.settingsPersonal;
  }

  save(settingsPersonal: SettingsPersonal): void {
    console.log(settingsPersonal);
    localStorage.setItem(this.settingsKey, JSON.stringify(settingsPersonal));
  }

}
