import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SettingsSecurityComponent } from './settings-security.component';

describe('SettingsSecurityComponent', () => {
  let component: SettingsSecurityComponent;
  let fixture: ComponentFixture<SettingsSecurityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsSecurityComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SettingsSecurityComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
