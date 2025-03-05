import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerComponent } from './action-trigger.component';

describe('ActionTriggerComponent', () => {
  let component: ActionTriggerComponent;
  let fixture: ComponentFixture<ActionTriggerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
