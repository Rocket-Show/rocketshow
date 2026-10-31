import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { HealthDialogComponent } from "./health-dialog.component";
import { HealthService } from "../../services/health.service";

/**
 * The health of the unit in a dialog of the advanced settings: loaded when it opens and again on refresh.
 */
describe("Health dialog", () => {
  let http: HttpTestingController;
  let bsModalRef: jasmine.SpyObj<any>;
  let toastGeneralErrorService: jasmine.SpyObj<any>;
  let dialog: HealthDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    http = TestBed.inject(HttpTestingController);
    bsModalRef = jasmine.createSpyObj("BsModalRef", ["hide"]);
    toastGeneralErrorService = jasmine.createSpyObj("ToastGeneralErrorService", ["show"]);

    dialog = new HealthDialogComponent(bsModalRef, new HealthService(TestBed.inject(HttpClient)),
      toastGeneralErrorService);
  });

  afterEach(() => {
    http.verify();
  });

  it("loads the health when it opens and again on refresh", () => {
    dialog.ngOnInit();

    expect(dialog.loading).toBeTrue();
    http.expectOne("system/health").flush({
      healthStatusSeverity: "DEGRADED",
      reasons: ["High temperature. CPU throttling possible"],
      softwareVersion: "3.1.0",
    });
    expect(dialog.loading).toBeFalse();
    expect(dialog.health.healthStatusSeverity).toBe("DEGRADED");
    expect(dialog.health.reasons).toEqual(["High temperature. CPU throttling possible"]);

    dialog.refresh();

    http.expectOne("system/health").flush({ healthStatusSeverity: "OK", reasons: [] });
    expect(dialog.health.healthStatusSeverity).toBe("OK");
  });

  it("tells why the health could not be loaded", () => {
    dialog.ngOnInit();

    http.expectOne("system/health").flush({ message: "Not available" }, { status: 500, statusText: "Server Error" });

    expect(dialog.loading).toBeFalse();
    expect(toastGeneralErrorService.show).toHaveBeenCalled();
  });

  it("closes", () => {
    dialog.close();

    expect(bsModalRef.hide).toHaveBeenCalled();
  });
});
