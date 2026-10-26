import { IntroComponent } from "./intro.component";

/**
 * The password of the wireless access point in the setup: hostapd only protects the access point with 8 to 63
 * characters, a shorter one leaves it open.
 */
describe("Setup of the access point", () => {
  // The rules only, without the dependencies of the component
  function setup(wifiApEnabled: boolean, sameAsAdmin: boolean, adminPassword: string, wifiApPassword?: string): IntroComponent {
    const intro = Object.create(IntroComponent.prototype) as IntroComponent;
    intro.wifiApEnabled = wifiApEnabled;
    intro.wifiApPasswordSame = sameAsAdmin;
    intro.adminPassword1 = adminPassword;
    intro.wifiApPassword1 = wifiApPassword;
    return intro;
  }

  it("refuses the admin password of 6 characters for the access point", () => {
    expect(setup(true, true, "123456").wifiApPasswordTooShort()).toBeTrue();
    expect(setup(true, true, "12345678").wifiApPasswordTooShort()).toBeFalse();
  });

  it("refuses an own password of the access point shorter than 8 or longer than 63 characters", () => {
    expect(setup(true, false, "admin-password", "short12").wifiApPasswordTooShort()).toBeTrue();
    expect(setup(true, false, "admin-password", "x".repeat(64)).wifiApPasswordTooShort()).toBeTrue();
    expect(setup(true, false, "admin-password", "").wifiApPasswordTooShort()).toBeTrue();
    expect(setup(true, false, "admin-password", "long-enough").wifiApPasswordTooShort()).toBeFalse();
  });

  it("needs no password without access point", () => {
    expect(setup(false, false, "123456", "").wifiApPasswordTooShort()).toBeFalse();
  });
});
