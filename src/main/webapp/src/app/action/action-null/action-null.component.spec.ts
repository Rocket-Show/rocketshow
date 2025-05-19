import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionNullComponent } from './action-null.component';

describe('ActionNullComponent', () => {
  let component: ActionNullComponent;
  let fixture: ComponentFixture<ActionNullComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionNullComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionNullComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
