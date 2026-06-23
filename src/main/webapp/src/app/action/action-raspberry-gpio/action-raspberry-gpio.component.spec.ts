import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionRaspberryGpioComponent } from './action-raspberry-gpio.component';

describe('ActionRaspberryGpioComponent', () => {
  let component: ActionRaspberryGpioComponent;
  let fixture: ComponentFixture<ActionRaspberryGpioComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionRaspberryGpioComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionRaspberryGpioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
