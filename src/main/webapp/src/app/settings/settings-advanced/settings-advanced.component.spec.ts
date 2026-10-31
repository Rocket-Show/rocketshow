import { of } from "rxjs";

import { SettingsAdvancedComponent } from "./settings-advanced.component";
import { HealthDialogComponent } from "../health-dialog/health-dialog.component";

/**
 * The advanced settings: the health of the unit, which only reads, and the self-test, which plays test tones and
 * switches outputs (see the specs of the SelfTestService).
 */
describe("Advanced settings", () => {
  let modalService: jasmine.SpyObj<any>;
  let selfTestService: jasmine.SpyObj<any>;
  let advanced: SettingsAdvancedComponent;

  beforeEach(() => {
    modalService = jasmine.createSpyObj("BsModalService", ["show"]);
    selfTestService = jasmine.createSpyObj("SelfTestService", ["run"]);
    const operatingSystemInformationService = { getOperatingSystemInformation: () => of({}) };
    const deviceInformationService = { getDeviceInformation: () => of({ available: true, serial: "RS-0001" }) };

    advanced = new SettingsAdvancedComponent(undefined, undefined, undefined, undefined, undefined,
      operatingSystemInformationService as any, deviceInformationService as any, undefined, modalService,
      selfTestService);
  });

  it("shows the health in a dialog, without a warning", () => {
    advanced.showHealth();

    expect(modalService.show).toHaveBeenCalledOnceWith(HealthDialogComponent, jasmine.anything());
  });

  it("runs the self-test like the provisioning", () => {
    advanced.runSelfTest();

    expect(selfTestService.run).toHaveBeenCalledTimes(1);
    expect(modalService.show).not.toHaveBeenCalled();
  });

  it("knows the serial of the unit for the link to the provisioning", () => {
    expect(advanced.deviceInformation.serial).toBe("RS-0001");
  });
});
