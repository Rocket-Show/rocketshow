import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionMidiComponent } from './action-midi.component';

describe('ActionMidiComponent', () => {
  let component: ActionMidiComponent;
  let fixture: ComponentFixture<ActionMidiComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionMidiComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionMidiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
