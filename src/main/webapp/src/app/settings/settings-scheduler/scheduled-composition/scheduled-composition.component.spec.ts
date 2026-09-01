import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScheduledCompositionComponent } from './scheduled-composition.component';

describe('ScheduledCompositionComponent', () => {
  let component: ScheduledCompositionComponent;
  let fixture: ComponentFixture<ScheduledCompositionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduledCompositionComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ScheduledCompositionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
