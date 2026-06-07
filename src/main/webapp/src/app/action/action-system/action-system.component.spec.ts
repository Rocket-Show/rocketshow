import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionSystemComponent } from './action-system.component';

describe('ActionSystemComponent', () => {
  let component: ActionSystemComponent;
  let fixture: ComponentFixture<ActionSystemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionSystemComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionSystemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
