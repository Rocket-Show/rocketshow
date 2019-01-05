import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SettingsLightingComponent } from './settings-lighting.component';

describe('SettingsLightingComponent', () => {
  let component: SettingsLightingComponent;
  let fixture: ComponentFixture<SettingsLightingComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SettingsLightingComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SettingsLightingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
