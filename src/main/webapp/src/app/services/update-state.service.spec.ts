import { TestBed } from '@angular/core/testing';

import { UpdateStateService } from './update-state.service';

describe('UpdateStateService', () => {
  let service: UpdateStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(UpdateStateService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
