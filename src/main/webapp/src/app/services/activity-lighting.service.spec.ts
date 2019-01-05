import { TestBed, inject } from '@angular/core/testing';

import { ActivityLightingService } from './activity-lighting.service';

describe('ActivityLightingService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ActivityLightingService]
    });
  });

  it('should be created', inject([ActivityLightingService], (service: ActivityLightingService) => {
    expect(service).toBeTruthy();
  }));
});
