import { HttpClient } from '@angular/common/http';
import { AudioBus } from './../models/audio-bus';
import { TranslateService } from '@ngx-translate/core';
import { AudioDevice } from './../models/audio-device';
import { MidiDevice } from './../models/midi-device';
import { Subject, Observable } from 'rxjs/Rx';
import { Settings } from './../models/settings';
import { Injectable } from '@angular/core';
import { Response } from '@angular/http';
import { Language } from '../models/language';

@Injectable()
export class SettingsService {

  // Fires, when the settings have changed
  settingsChanged: Subject<void> = new Subject<void>();

  languages: Language[] = [];
  settings: Settings;
  observable: Observable<Settings>;

  constructor(
    private http: HttpClient,
    private translateService: TranslateService) {

    let language: Language;

    language = new Language();
    language.key = 'en';
    language.name = 'English';
    this.languages.push(language);

    language = new Language();
    language.key = 'de';
    language.name = 'Deutsch';
    this.languages.push(language);
  }

  getSettings(clearCache: boolean = false): Observable<Settings> {
    if (clearCache) {
      this.settings = undefined;
      this.observable = undefined;
    }

    if (this.settings) {
      return Observable.of(this.settings);
    }

    if (this.observable) {
      return this.observable;
    }

    this.observable = this.http.get('system/settings')
      .map(response => {
        if (!this.settings) {
          this.settings = new Settings(response);
        }
        this.observable = undefined;

        return this.settings;
      });

    return this.observable;
  }

  saveSettings(settings: Settings): Observable<Object> {
    return this.http.post('system/settings', JSON.stringify(settings));
  }

  private apiGetMidiDevices(url: string) {
    return this.http.get('midi/' + url)
      .map((response: Array<Object>) => {
        let deviceList: MidiDevice[] = [];

        for (let midiDevice of response) {
          deviceList.push(new MidiDevice(midiDevice));
        }

        return deviceList;
      });
  }

  getMidiInDevices(): Observable<MidiDevice[]> {
    return this.apiGetMidiDevices('in-devices');
  }

  getMidiOutDevices(): Observable<MidiDevice[]> {
    return this.apiGetMidiDevices('out-devices');
  }

  getAudioDevices(): Observable<AudioDevice[]> {
    return this.http.get('audio/devices')
      .map((response: Array<Object>) => {
        let deviceList: AudioDevice[] = [];

        for (let audioDevice of response) {
          deviceList.push(new AudioDevice(audioDevice));
        }

        return deviceList;
      });
  }

  addAudioBus(settings: Settings): Observable<void> {
    return this.translateService.get('settings.audio-bus-name-placeholder').map(result => {
      let audioBus: AudioBus = new AudioBus();
      audioBus.name = result + ' ' + (settings.audioBusList.length + 1);
      settings.audioBusList.push(audioBus);
    });
  }

}
