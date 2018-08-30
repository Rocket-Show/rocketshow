import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { LeadSheetComponent } from './lead-sheet.component';

describe('LeadSheetComponent', () => {
  let component: LeadSheetComponent;
  let fixture: ComponentFixture<LeadSheetComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ LeadSheetComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LeadSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
