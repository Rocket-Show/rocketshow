import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { AuthService, AuthState } from "./auth.service";

/**
 * The password of the device is remembered in the browser, so the user does not have to enter it again each time
 * the device rebooted and forgot the session.
 */
describe("AuthService", () => {
  const passwordKey = "adminPassword";

  const loggedOut: AuthState = { authenticated: false, passwordConfigured: true };
  const loggedIn: AuthState = { authenticated: true, passwordConfigured: true, username: "admin", roles: ["ROLE_ADMIN"] };
  const notSetUp: AuthState = { authenticated: false, passwordConfigured: false };

  let http: HttpTestingController;

  // Starts the app with what the device answers about the session
  function start(me: AuthState): AuthService {
    const service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne("auth/me").flush(me);
    return service;
  }

  beforeEach(() => {
    localStorage.removeItem(passwordKey);

    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(passwordKey);
  });

  it("remembers the password after logging in", () => {
    const service = start(loggedOut);

    service.login("secret").subscribe();
    http.expectOne("auth/login").flush(loggedIn);

    expect(localStorage.getItem(passwordKey)).toBe("secret");
  });

  it("does not remember a wrong password", () => {
    const service = start(loggedOut);

    service.login("wrong").subscribe({ error: () => { } });
    http.expectOne("auth/login").flush(null, { status: 401, statusText: "Unauthorized" });

    expect(localStorage.getItem(passwordKey)).toBeNull();
  });

  it("remembers the password of the setup", () => {
    const service = start(notSetUp);

    service.setup("en", "Rocket Show", "secret", false, "", false).subscribe();
    http.expectOne("auth/setup").flush(loggedIn);

    expect(localStorage.getItem(passwordKey)).toBe("secret");
  });

  it("logs in again with the remembered password after the device rebooted", () => {
    localStorage.setItem(passwordKey, "secret");

    const service = start(loggedOut);
    const login = http.expectOne("auth/login");
    expect(login.request.body).toEqual({ password: "secret" });
    login.flush(loggedIn);

    expect(service.currentState.authenticated).toBeTrue();
    expect(service.initiallyLoaded).toBeTrue();
  });

  it("does not log in again while still logged in", () => {
    localStorage.setItem(passwordKey, "secret");

    const service = start(loggedIn);

    http.expectNone("auth/login");
    expect(service.currentState.authenticated).toBeTrue();
  });

  it("asks for the password, if the remembered one got changed elsewhere", () => {
    localStorage.setItem(passwordKey, "old");

    const service = start(loggedOut);
    http.expectOne("auth/login").flush(null, { status: 401, statusText: "Unauthorized" });

    expect(service.currentState).toEqual(loggedOut);
    expect(localStorage.getItem(passwordKey)).toBeNull();
  });

  it("keeps the remembered password, if the device could not check it", () => {
    localStorage.setItem(passwordKey, "secret");

    const service = start(loggedOut);
    http.expectOne("auth/login").flush(null, { status: 500, statusText: "Internal Server Error" });

    expect(service.currentState).toEqual(loggedOut);
    expect(localStorage.getItem(passwordKey)).toBe("secret");
  });

  it("forgets the password after a factory reset", () => {
    localStorage.setItem(passwordKey, "secret");

    const service = start(notSetUp);

    http.expectNone("auth/login");
    expect(service.currentState).toEqual(notSetUp);
    expect(localStorage.getItem(passwordKey)).toBeNull();
  });

  it("updates the remembered password, when it gets changed", () => {
    localStorage.setItem(passwordKey, "old");
    const service = start(loggedIn);

    service.changePassword("old", "new").subscribe();
    http.expectOne("auth/change-password").flush(loggedIn);

    expect(localStorage.getItem(passwordKey)).toBe("new");
  });

  it("keeps the remembered password, if the change got refused", () => {
    localStorage.setItem(passwordKey, "old");
    const service = start(loggedIn);

    service.changePassword("wrong", "new").subscribe({ error: () => { } });
    http.expectOne("auth/change-password").flush(null, { status: 401, statusText: "Unauthorized" });

    expect(localStorage.getItem(passwordKey)).toBe("old");
  });

  it("notices, when the device forgot the session, without changing the state", () => {
    const service = start(loggedIn);
    let sessionLost: boolean;

    service.sessionLost().subscribe((result) => sessionLost = result);
    http.expectOne("auth/me").flush(loggedOut);

    expect(sessionLost).toBeTrue();
    expect(service.currentState.authenticated).toBeTrue();
  });

  it("does not take an unreachable device for a lost session", () => {
    const service = start(loggedIn);
    let sessionLost: boolean;

    service.sessionLost().subscribe((result) => sessionLost = result);
    http.expectOne("auth/me").error(new ProgressEvent("error"));

    expect(sessionLost).toBeFalse();
  });

  it("forgets the password after logging out", () => {
    localStorage.setItem(passwordKey, "secret");
    const service = start(loggedIn);

    service.logout().subscribe();
    http.expectOne("auth/logout").flush(null);

    expect(localStorage.getItem(passwordKey)).toBeNull();
  });
});
