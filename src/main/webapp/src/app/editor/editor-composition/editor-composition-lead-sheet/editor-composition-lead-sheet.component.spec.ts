import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { EditorCompositionLeadSheetComponent } from './editor-composition-lead-sheet.component';

describe('EditorCompositionLeadSheetComponent', () => {
  let component: EditorCompositionLeadSheetComponent;
  let fixture: ComponentFixture<EditorCompositionLeadSheetComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditorCompositionLeadSheetComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditorCompositionLeadSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
