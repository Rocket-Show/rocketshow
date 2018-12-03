import { TestBed, inject } from '@angular/core/testing';

import { LeadSheetService } from './lead-sheet.service';

describe('LeadSheetService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LeadSheetService]
    });
  });

  it('should be created', inject([LeadSheetService], (service: LeadSheetService) => {
    expect(service).toBeTruthy();
  }));
});
