import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { of } from "rxjs";

import { SelfTestService } from "./self-test.service";
import { SelfTestResultDialogComponent } from "../settings/self-test-result-dialog/self-test-result-dialog.component";

/**
 * Running the self-test of the unit from the advanced settings or the provisioning, which plays test tones and
 * switches outputs, and showing its checks in a dialog.
 */
describe("Self-test", () => {
  let http: HttpTestingController;
  let warningDialogService: jasmine.SpyObj<any>;
  let waitDialogService: jasmine.SpyObj<any>;
  let toastGeneralErrorService: jasmine.SpyObj<any>;
  let modalService: jasmine.SpyObj<any>;
  let selfTestService: SelfTestService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    http = TestBed.inject(HttpTestingController);
    warningDialogService = jasmine.createSpyObj("WarningDialogService", ["show"]);
    waitDialogService = jasmine.createSpyObj("WaitDialogService", ["show", "hide"]);
    toastGeneralErrorService = jasmine.createSpyObj("ToastGeneralErrorService", ["show"]);
    modalService = jasmine.createSpyObj("BsModalService", ["show"]);

    selfTestService = new SelfTestService(TestBed.inject(HttpClient), warningDialogService, waitDialogService,
      toastGeneralErrorService, modalService);
  });

  afterEach(() => {
    http.verify();
  });

  it("runs the self-test only after the warning", () => {
    warningDialogService.show.and.returnValue(of(false));

    selfTestService.run();

    expect(warningDialogService.show).toHaveBeenCalledWith("settings.self-test.warning");
    http.expectNone("system/test");
    expect(modalService.show).not.toHaveBeenCalled();
  });

  it("shows the checks of the unit in a dialog", () => {
    warningDialogService.show.and.returnValue(of(true));
    let waitDialogHidden: boolean;
    modalService.show.and.callFake(() => {
      waitDialogHidden = waitDialogService.hide.calls.any();
    });

    selfTestService.run();

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

    // Instead of the wait dialog
    expect(waitDialogHidden).toBeTrue();
    expect(modalService.show).toHaveBeenCalledOnceWith(SelfTestResultDialogComponent, jasmine.anything());
    const selfTestResult = modalService.show.calls.mostRecent().args[1].initialState.selfTestResult;
    expect(selfTestResult.result).toBe("FAILED");
    expect(selfTestResult.checks.map((check) => check.name)).toEqual(["audio", "midi-loopback"]);
    expect(selfTestResult.checks[0].target).toBe("USB Audio, 2 channels");
    expect(selfTestResult.checks[1].details).toBe("196 of 200 messages received");
  });

  it("tells why the unit refused, e.g. while a composition plays", () => {
    warningDialogService.show.and.returnValue(of(true));

    selfTestService.run();
    http.expectOne("system/test").flush({ message: "A composition is playing" }, { status: 409, statusText: "Conflict" });

    expect(toastGeneralErrorService.show).toHaveBeenCalled();
    expect(waitDialogService.hide).toHaveBeenCalled();
    expect(modalService.show).not.toHaveBeenCalled();
  });
});
