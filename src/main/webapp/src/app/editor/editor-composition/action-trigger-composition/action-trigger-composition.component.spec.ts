import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTriggerCompositionComponent } from './action-trigger-composition.component';

describe('ActionTriggerCompositionComponent', () => {
  let component: ActionTriggerCompositionComponent;
  let fixture: ComponentFixture<ActionTriggerCompositionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTriggerCompositionComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTriggerCompositionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
