import { TestBed, inject } from '@angular/core/testing';

import { ActivityAudioService } from './activity-audio.service';

describe('ActivityAudioService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ActivityAudioService]
    });
  });

  it('should be created', inject([ActivityAudioService], (service: ActivityAudioService) => {
    expect(service).toBeTruthy();
  }));
});
