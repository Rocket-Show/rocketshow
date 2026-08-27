import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SettingsSchedulerComponent } from './settings-scheduler.component';

describe('SettingsSchedulerComponent', () => {
  let component: SettingsSchedulerComponent;
  let fixture: ComponentFixture<SettingsSchedulerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsSchedulerComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SettingsSchedulerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
