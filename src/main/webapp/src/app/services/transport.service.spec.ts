import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { TransportService } from "./transport.service";

describe("TransportService", () => {
  let service: TransportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TransportService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(TransportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it("goes on from a hold point", () => {
    service.go().subscribe();
    http.expectOne({ method: "POST", url: "transport/go" }).flush(null);
  });
});
