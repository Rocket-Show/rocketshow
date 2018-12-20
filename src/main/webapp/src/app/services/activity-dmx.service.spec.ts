import { TestBed, inject } from '@angular/core/testing';

import { ActivityDmxService } from './activity-dmx.service';

describe('ActivityDmxService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ActivityDmxService]
    });
  });

  it('should be created', inject([ActivityDmxService], (service: ActivityDmxService) => {
    expect(service).toBeTruthy();
  }));
});
