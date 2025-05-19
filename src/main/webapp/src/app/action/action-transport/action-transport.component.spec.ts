import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionTransportComponent } from './action-transport.component';

describe('ActionTransportComponent', () => {
  let component: ActionTransportComponent;
  let fixture: ComponentFixture<ActionTransportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionTransportComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ActionTransportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
