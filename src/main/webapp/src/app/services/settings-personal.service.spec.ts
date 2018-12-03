import { TestBed, inject } from '@angular/core/testing';

import { SettingsPersonalService } from './settings-personal.service';

describe('SettingsPersonalService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SettingsPersonalService]
    });
  });

  it('should be created', inject([SettingsPersonalService], (service: SettingsPersonalService) => {
    expect(service).toBeTruthy();
  }));
});
