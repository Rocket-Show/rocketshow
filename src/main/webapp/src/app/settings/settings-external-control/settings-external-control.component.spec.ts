import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SettingsExternalControlComponent } from './settings-external-control.component';

describe('SettingsExternalControlComponent', () => {
  let component: SettingsExternalControlComponent;
  let fixture: ComponentFixture<SettingsExternalControlComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsExternalControlComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(SettingsExternalControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
