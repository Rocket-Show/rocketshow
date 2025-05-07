import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerRaspberryGpioComponent } from './action-trigger-raspberry-gpio.component';

describe('ActionTriggerRaspberryGpioComponent', () => {
  let component: ActionTriggerRaspberryGpioComponent;
  let fixture: ComponentFixture<ActionTriggerRaspberryGpioComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerRaspberryGpioComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerRaspberryGpioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
