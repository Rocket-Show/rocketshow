import { TestBed } from '@angular/core/testing';

import { DeviceInformationService } from './device-information.service';

describe('DeviceInformationService', () => {
  let service: DeviceInformationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DeviceInformationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
