import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerMidiNoteOnComponent } from './action-trigger-midi-note-on.component';

describe('ActionTriggerMidiNoteOnComponent', () => {
  let component: ActionTriggerMidiNoteOnComponent;
  let fixture: ComponentFixture<ActionTriggerMidiNoteOnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerMidiNoteOnComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerMidiNoteOnComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
