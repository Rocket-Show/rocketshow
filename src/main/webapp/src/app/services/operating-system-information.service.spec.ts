import { TestBed, inject } from '@angular/core/testing';

import { OperatingSystemInformationService } from './operating-system-information.service';

describe('OperatingSystemInformationService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OperatingSystemInformationService]
    });
  });

  it('should be created', inject([OperatingSystemInformationService], (service: OperatingSystemInformationService) => {
    expect(service).toBeTruthy();
  }));
});
