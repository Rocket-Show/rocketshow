import { TestBed } from '@angular/core/testing';

import { ReloadClearCacheService } from './reload-clear-cache.service';

describe('ReloadClearCacheService', () => {
  let service: ReloadClearCacheService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ReloadClearCacheService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
