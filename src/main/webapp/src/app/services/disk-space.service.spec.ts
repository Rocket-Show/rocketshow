import { TestBed, inject } from '@angular/core/testing';

import { DiskSpaceService } from './disk-space.service';

describe('DiskSpaceService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DiskSpaceService]
    });
  });

  it('should be created', inject([DiskSpaceService], (service: DiskSpaceService) => {
    expect(service).toBeTruthy();
  }));
});
