import { TestBed, inject } from '@angular/core/testing';

import { AppHttpInterceptor } from './app-http-interceptor';

describe('AppHttpInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AppHttpInterceptor]
    });
  });

  it('should be created', inject([AppHttpInterceptor], (service: AppHttpInterceptor) => {
    expect(service).toBeTruthy();
  }));
});
