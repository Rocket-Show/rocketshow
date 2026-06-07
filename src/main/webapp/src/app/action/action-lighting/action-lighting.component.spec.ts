import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionLightingComponent } from './action-lighting.component';

describe('ActionLightingComponent', () => {
  let component: ActionLightingComponent;
  let fixture: ComponentFixture<ActionLightingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionLightingComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionLightingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
