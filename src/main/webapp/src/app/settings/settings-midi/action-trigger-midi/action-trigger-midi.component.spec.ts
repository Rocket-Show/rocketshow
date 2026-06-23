import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerMidiComponent } from './action-trigger-midi.component';

describe('ActionTriggerMidiComponent', () => {
  let component: ActionTriggerMidiComponent;
  let fixture: ComponentFixture<ActionTriggerMidiComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerMidiComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerMidiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
