import { TestBed, inject } from '@angular/core/testing';

import { UuidService } from './uuid.service';

describe('UuidService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UuidService]
    });
  });

  it('should be created', inject([UuidService], (service: UuidService) => {
    expect(service).toBeTruthy();
  }));
});
