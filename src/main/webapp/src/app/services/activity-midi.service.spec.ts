import { TestBed, inject } from '@angular/core/testing';

import { ActivityMidiService } from './activity-midi.service';

describe('ActivityMidiService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ActivityMidiService]
    });
  });

  it('should be created', inject([ActivityMidiService], (service: ActivityMidiService) => {
    expect(service).toBeTruthy();
  }));
});
