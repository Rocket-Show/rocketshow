import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { TempoService } from "./tempo.service";

/**
 * The tempo from outside, which the device keeps: each change is one request.
 */
describe("TempoService", () => {
  let service: TempoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(TempoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it("taps the beat and sets the first beat of a bar", () => {
    service.tap().subscribe();
    http.expectOne({ method: "POST", url: "tempo/tap" }).flush(null);

    service.beatOne().subscribe();
    http.expectOne({ method: "POST", url: "tempo/beat-one" }).flush(null);
  });

  it("sets the tempo, and goes back to the one of what plays", () => {
    service.set(128.5).subscribe();
    http.expectOne({ method: "POST", url: "tempo/set?value=128.5" }).flush(null);

    service.set(undefined).subscribe();
    http.expectOne({ method: "POST", url: "tempo/set" }).flush(null);
  });

  it("halves, doubles and nudges the tempo", () => {
    service.halve().subscribe();
    http.expectOne({ method: "POST", url: "tempo/halve" }).flush(null);

    service.double().subscribe();
    http.expectOne({ method: "POST", url: "tempo/double" }).flush(null);

    service.nudge(1).subscribe();
    http.expectOne({ method: "POST", url: "tempo/nudge?value=1" }).flush(null);

    service.nudge(-1).subscribe();
    http.expectOne({ method: "POST", url: "tempo/nudge?value=-1" }).flush(null);
  });
});
