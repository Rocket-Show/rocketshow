import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SettingsBandComponent } from './settings-band.component';

describe('SettingsBandComponent', () => {
  let component: SettingsBandComponent;
  let fixture: ComponentFixture<SettingsBandComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SettingsBandComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SettingsBandComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
