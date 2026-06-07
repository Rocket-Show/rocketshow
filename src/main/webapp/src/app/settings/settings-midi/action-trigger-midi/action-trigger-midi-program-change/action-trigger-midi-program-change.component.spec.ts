import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerMidiProgramChangeComponent } from './action-trigger-midi-program-change.component';

describe('ActionTriggerMidiProgramChangeComponent', () => {
  let component: ActionTriggerMidiProgramChangeComponent;
  let fixture: ComponentFixture<ActionTriggerMidiProgramChangeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerMidiProgramChangeComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerMidiProgramChangeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
