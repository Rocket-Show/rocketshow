import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { TranslateService } from "@ngx-translate/core";
import { NEVER, of } from "rxjs";

import { SettingsComponent } from "./settings.component";
import { SettingsService } from "../services/settings.service";
import { UuidService } from "../services/uuid.service";

/**
 * The settings form: what it selects when loading the settings of the unit and what it sends when saving them.
 */
describe("Settings", () => {
  const translations: { [key: string]: string } = {
    "settings.audio-bus-name-placeholder": "Bus",
    "intro.default-unit-name": "Rocket Show",
    "settings.remote-device-name-placeholder": "Unit",
  };

  // Like ngx-translate: the translation of a key or of a list of keys
  const translateService = {
    get: (keys: string | string[]) =>
      of(Array.isArray(keys) ? Object.fromEntries(keys.map((key) => [key, translations[key] ?? key])) : translations[keys] ?? keys),
    use: () => of({}),
  };

  let http: HttpTestingController;
  let settingsService: SettingsService;
  let toastrService: jasmine.SpyObj<any>;
  let settings: SettingsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SettingsService,
        UuidService,
        { provide: TranslateService, useValue: translateService },
      ],
    });

    http = TestBed.inject(HttpTestingController);
    settingsService = TestBed.inject(SettingsService);
    toastrService = jasmine.createSpyObj("ToastrService", ["success", "error"]);
    const settingsPersonalService = { getSettings: () => ({}), save: () => {}, settingsChanged: { next: () => {} } };
    const toastGeneralErrorService = { show: () => NEVER };

    settings = new SettingsComponent(settingsService, settingsPersonalService as any, undefined, translateService as any,
      toastrService, toastGeneralErrorService as any);
  });

  afterEach(() => {
    http.verify();
  });

  // Like a unit answering: its settings and its devices
  function load(unitSettings: any, midiInDevices: any[] = [], midiOutDevices: any[] = []) {
    settings.ngOnInit();
    http.expectOne("system/settings").flush({ audioBusList: [], remoteDeviceList: [], ...unitSettings });
    http.expectOne("midi/in-devices").flush(midiInDevices);
    http.expectOne("midi/out-devices").flush(midiOutDevices);
    http.expectOne("audio/devices").flush([]);
  }

  function savedSettings(): any {
    settings.save();
    return JSON.parse(http.expectOne("system/settings").request.body);
  }

  it("selects no MIDI device when none is set and the first one when the set one is gone", () => {
    load({ midiOutDevice: { id: 3, name: "Unplugged", vendor: "Roland" } }, [], [{ id: 1, name: "Port 1", vendor: "Yamaha" }]);

    expect(settings.loading).toBeFalse();
    expect(settingsService.settings.midiInDevice.id).toBe(-1);
    expect(settingsService.settings.midiOutDevice.name).toBe("Port 1");
  });

  it("keeps a MIDI device which is connected", () => {
    load({ midiInDevice: { id: 2, name: "Keys", vendor: "Nord" } }, [{ id: 1, name: "Pads", vendor: "Akai" }, { id: 2, name: "Keys", vendor: "Nord" }]);

    expect(settingsService.settings.midiInDevice.name).toBe("Keys");
  });

  it("adds an audio bus to a unit without", () => {
    load({});

    expect(settingsService.settings.audioBusList.length).toBe(1);
    expect(settingsService.settings.audioBusList[0].name).toBe("Bus 1");
    expect(settingsService.settings.audioBusList[0].uuid).toBeTruthy();
  });

  it("fills in what is missing before saving", () => {
    load({ deviceName: "", wlanApSsid: "", remoteDeviceList: [{ name: "", host: "" }, { name: "Drums", host: "10.0.0.2" }] });

    const saved = savedSettings();

    expect(saved.deviceName).toBe("Rocket Show");
    expect(saved.wlanApSsid).toBe("Rocket Show");
    expect(saved.remoteDeviceList[0].name).toBe("Unit 1");
    expect(saved.remoteDeviceList[0].host).toBe("192.168.1.22");
    expect(saved.remoteDeviceList[1].name).toBe("Drums");
    expect(saved.remoteDeviceList[1].host).toBe("10.0.0.2");
  });

  it("keeps the settings the web app doesn't know and sends no MIDI device for [None]", () => {
    load({ version: 7, settingOfALaterRelease: { kept: true }, deviceName: "Stage left" });

    const saved = savedSettings();

    expect(saved.version).toBe(7);
    expect(saved.settingOfALaterRelease).toEqual({ kept: true });
    expect(saved.midiInDevice).toBeNull();
    expect(saved.midiOutDevice).toBeNull();
  });

  it("refuses a passphrase which would leave the access point open", () => {
    load({ deviceName: "Stage left", wlanApEnable: true, wlanApPassphrase: "1234567" });

    settings.save();

    // Nothing saved
    http.expectNone("system/settings");
    expect(toastrService.error).toHaveBeenCalled();
    expect(settings.savingSettings).toBeFalse();
  });

  it("saves a passphrase of 8 characters", () => {
    load({ deviceName: "Stage left", wlanApEnable: true, wlanApPassphrase: "12345678" });

    expect(savedSettings().wlanApPassphrase).toBe("12345678");
  });
});
