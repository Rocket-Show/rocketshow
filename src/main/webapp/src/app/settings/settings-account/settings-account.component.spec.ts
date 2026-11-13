import { TestBed } from "@angular/core/testing";
import { HttpErrorResponse, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { SettingsAccountComponent } from "./settings-account.component";
import { AccountService } from "../../services/account.service";

/**
 * The account of the online designer in the settings: linking the device with an email and a password, and what the
 * user is told when it doesn't work.
 */
describe("Account settings", () => {
  let http: HttpTestingController;
  let account: SettingsAccountComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    http = TestBed.inject(HttpTestingController);
    account = new SettingsAccountComponent(TestBed.inject(AccountService));
    account.ngOnInit();
  });

  afterEach(() => {
    http.verify();
  });

  function logIn() {
    http.expectOne("account").flush({ linked: false });
    account.email = "band@example.com";
    account.password = "password";
    account.login();
    return http.expectOne("account/login");
  }

  it("shows the account the device is linked to", () => {
    http.expectOne("account").flush({ linked: true, username: "band@example.com" });

    expect(account.status.linked).toBeTrue();
    expect(account.status.username).toBe("band@example.com");
  });

  it("links the device with the email and the password", () => {
    const request = logIn();

    expect(request.request.method).toBe("POST");
    expect(request.request.body).toEqual({ email: "band@example.com", password: "password" });
    expect(account.busy).toBeTrue();

    request.flush({ linked: true, username: "band@example.com" });

    expect(account.status.linked).toBeTrue();
    expect(account.password).toBe("");
    expect(account.busy).toBeFalse();
    expect(account.error).toBeUndefined();
  });

  it("tells about a wrong password", () => {
    logIn().flush({ error: "wrong-credentials" }, { status: 400, statusText: "Bad Request" });

    expect(account.status.linked).toBeFalse();
    expect(account.error).toBe("settings.account.wrong-credentials");
    expect(account.busy).toBeFalse();
  });

  it("tells when rocketshow.net can't be reached", () => {
    logIn().flush({ error: "api-unreachable" }, { status: 502, statusText: "Bad Gateway" });

    expect(account.error).toBe("settings.account.unreachable");
  });

  it("tells about any other error", () => {
    logIn().error(new ProgressEvent("error"));

    expect(account.error).toBe("settings.account.error");
  });

  it("unlinks the device", () => {
    http.expectOne("account").flush({ linked: true, username: "band@example.com" });

    account.logout();
    const request = http.expectOne("account/logout");
    expect(request.request.method).toBe("POST");
    request.flush({ linked: false });

    expect(account.status.linked).toBeFalse();
    expect(account.busy).toBeFalse();
  });
});
