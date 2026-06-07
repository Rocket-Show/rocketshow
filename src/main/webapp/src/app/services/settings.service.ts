import { HttpClient } from "@angular/common/http";
import { LanInfo } from "./../models/lan-info";
import { AudioBus } from "./../models/audio-bus";
import { TranslateService } from "@ngx-translate/core";
import { AudioDevice } from "./../models/audio-device";
import { MidiDevice } from "./../models/midi-device";
import { Subject, Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { Settings } from "./../models/settings";
import { Injectable } from "@angular/core";
import { Language } from "../models/language";
import { OlaPlugin } from "../models/ola-plugin";
import { OlaPort } from "../models/ola-port";
import { RaspberryPiPin } from "../models/raspberry-pi-pin";
import { LightingUniverse } from "../models/lighting-universe";
import { UuidService } from "./uuid.service";

@Injectable()
export class SettingsService {
  // Fires, when the settings have changed
  settingsChanged: Subject<void> = new Subject<void>();

  languages: Language[] = [];
  originalSettings: any; // Raw settings response to keep attributes unknown to the webapp
  settings: Settings;

  observable: Observable<Settings>;

  raspberryPiPinIdList: RaspberryPiPin[] = [];

  constructor(
    private http: HttpClient,
    private translateService: TranslateService,
    private uuidService: UuidService
  ) {
    let language: Language;

    language = new Language();
    language.key = "de";
    language.name = "Deutsch";
    this.languages.push(language);

    language = new Language();
    language.key = "en";
    language.name = "English";
    this.languages.push(language);

    language = new Language();
    language.key = "es";
    language.name = "Español";
    this.languages.push(language);

    language = new Language();
    language.key = "fr";
    language.name = "Français";
    this.languages.push(language);

    language = new Language();
    language.key = "it";
    language.name = "Italiano";
    this.languages.push(language);

    language = new Language();
    language.key = "pt";
    language.name = "Português";
    this.languages.push(language);

    language = new Language();
    language.key = "zh";
    language.name = "中文";
    this.languages.push(language);

    this.raspberryPiPinIdList.push(new RaspberryPiPin(4));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(5));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(6));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(7));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(8));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(9));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(10));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(11));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(12));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(13));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(14));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(15));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(16));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(17));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(18));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(19));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(20));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(21));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(22));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(23));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(24));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(25));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(26));
    this.raspberryPiPinIdList.push(new RaspberryPiPin(27));
  }

  getSettings(clearCache: boolean = false): Observable<Settings> {
    if (clearCache) {
      this.settings = undefined;
      this.observable = undefined;
    }

    if (this.settings) {
      return of(this.settings);
    }

    if (this.observable) {
      return this.observable;
    }

    this.observable = this.http.get("system/settings").pipe(
      map((response) => {
        if (!this.settings) {
          this.originalSettings = response;
          this.settings = new Settings(response);
        }
        this.observable = undefined;

        return this.settings;
      })
    );

    return this.observable;
  }

  saveSettings(): Observable<Object> {
    const mergedSettings = { ...this.originalSettings, ...this.settings }; // Merge known and unknown fields
    this.removeNoneMidiDevices(mergedSettings);
    return this.http.post("system/settings", JSON.stringify(mergedSettings));
  }

  private removeNoneMidiDevices(settings: Settings) {
    // Remove any MIDI device placeholders (id === -1) so server sees them as not selected
    if (settings.midiInDevice && settings.midiInDevice.id === -1) {
      settings.midiInDevice = null;
    }
    if (settings.midiOutDevice && settings.midiOutDevice.id === -1) {
      settings.midiOutDevice = null;
    }
  }

  updateLightingOlaPlugins(olaPluginList: OlaPlugin[]): Observable<Object> {
    return this.http.post(
      "system/settings-lighting-ola-plugins",
      JSON.stringify(olaPluginList)
    );
  }

  updateLightingUniverses(lightingUniverseList: LightingUniverse[]): Observable<Object> {
    return this.http.post(
      "system/settings-lighting-universes",
      JSON.stringify(lightingUniverseList)
    );
  }

  private apiGetMidiDevices(url: string) {
    return this.http.get("midi/" + url).pipe(
      map((response: Array<Object>) => {
        let deviceList: MidiDevice[] = [];

        for (let midiDevice of response) {
          deviceList.push(new MidiDevice(midiDevice));
        }

        return deviceList;
      })
    );
  }

  getMidiInDevices(): Observable<MidiDevice[]> {
    return this.apiGetMidiDevices("in-devices");
  }

  getMidiOutDevices(): Observable<MidiDevice[]> {
    return this.apiGetMidiDevices("out-devices");
  }

  getAudioDevices(): Observable<AudioDevice[]> {
    return this.http.get("audio/devices").pipe(
      map((response: Array<Object>) => {
        let deviceList: AudioDevice[] = [];

        for (let audioDevice of response) {
          deviceList.push(new AudioDevice(audioDevice));
        }

        return deviceList;
      })
    );
  }

  getOlaPluginList(): Observable<OlaPlugin[]> {
    return this.http.get("lighting/ola-plugins").pipe(
      map((response: Array<Object>) => {
        let olaPluginList: OlaPlugin[] = [];

        for (let olaPlugin of response) {
          olaPluginList.push(new OlaPlugin(olaPlugin));
        }

        return olaPluginList;
      })
    );
  }

  getOlaOutputPortList(): Observable<OlaPort[]> {
    return this.http.get("lighting/ola-output-ports").pipe(
      map((response: Array<Object>) => {
        let olaPortList: OlaPort[] = [];

        for (let olaPort of response) {
          olaPortList.push(new OlaPort(olaPort));
        }

        return olaPortList;
      })
    );
  }

  getLanInfo(): Observable<LanInfo> {
    return this.http.get<LanInfo>("system/lan-info");
  }

  getMaxAudioChannels(): Observable<number> {
    return this.http.get("audio/max-channels").pipe(
      map((response: number) => {
        return response;
      })
    );
  }

  addAudioBus(settings: Settings): Observable<void> {
    return this.translateService
      .get("settings.audio-bus-name-placeholder")
      .pipe(
        map((result) => {
          let audioBus: AudioBus = new AudioBus();
          audioBus.uuid = this.uuidService.getUuid();
          audioBus.name = result + " " + (settings.audioBusList.length + 1);
          settings.audioBusList.push(audioBus);
        })
      );
  }
}
