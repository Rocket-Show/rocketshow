import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { of } from "rxjs";

import { SettingsAdvancedComponent } from "./settings-advanced.component";

/**
 * The advanced settings: running the self-test of the unit, which plays test tones and switches outputs.
 */
describe("Advanced settings", () => {
  let http: HttpTestingController;
  let warningDialogService: jasmine.SpyObj<any>;
  let waitDialogService: jasmine.SpyObj<any>;
  let toastGeneralErrorService: jasmine.SpyObj<any>;
  let advanced: SettingsAdvancedComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    http = TestBed.inject(HttpTestingController);
    warningDialogService = jasmine.createSpyObj("WarningDialogService", ["show"]);
    waitDialogService = jasmine.createSpyObj("WaitDialogService", ["show", "hide"]);
    toastGeneralErrorService = jasmine.createSpyObj("ToastGeneralErrorService", ["show"]);
    const operatingSystemInformationService = { getOperatingSystemInformation: () => of({}) };
    const deviceInformationService = { getDeviceInformation: () => of({ available: false }) };

    advanced = new SettingsAdvancedComponent(undefined, warningDialogService, waitDialogService, TestBed.inject(HttpClient),
      undefined, operatingSystemInformationService as any, deviceInformationService as any, undefined,
      toastGeneralErrorService);
  });

  afterEach(() => {
    http.verify();
  });

  it("runs the self-test only after the warning", () => {
    warningDialogService.show.and.returnValue(of(false));

    advanced.runSelfTest();

    expect(warningDialogService.show).toHaveBeenCalledWith("settings.self-test.warning");
    http.expectNone("system/test");
    expect(advanced.selfTestResult).toBeUndefined();
  });

  it("shows the checks of the unit", () => {
    warningDialogService.show.and.returnValue(of(true));

    advanced.runSelfTest();

    expect(waitDialogService.show).toHaveBeenCalledWith("settings.self-test.wait");
    const request = http.expectOne("system/test");
    expect(request.request.method).toBe("POST");
    request.flush({
      result: "FAILED",
      startedAt: "2026-10-27T13:00:00Z",
      checks: [
        { name: "audio", target: "USB Audio, 2 channels", result: "PASSED", details: "-20.0 to -20.1 dBFS on every channel" },
        { name: "midi-loopback", result: "FAILED", details: "196 of 200 messages received" },
      ],
    });

    expect(waitDialogService.hide).toHaveBeenCalled();
    expect(advanced.selfTestResult.result).toBe("FAILED");
    expect(advanced.selfTestResult.checks.map((check) => check.name)).toEqual(["audio", "midi-loopback"]);
    expect(advanced.selfTestResult.checks[0].target).toBe("USB Audio, 2 channels");
    expect(advanced.selfTestResult.checks[1].details).toBe("196 of 200 messages received");
  });

  it("tells why the unit refused, e.g. while a composition plays", () => {
    warningDialogService.show.and.returnValue(of(true));

    advanced.runSelfTest();
    http.expectOne("system/test").flush({ message: "A composition is playing" }, { status: 409, statusText: "Conflict" });

    expect(toastGeneralErrorService.show).toHaveBeenCalled();
    expect(waitDialogService.hide).toHaveBeenCalled();
    expect(advanced.selfTestResult).toBeUndefined();
  });
});
