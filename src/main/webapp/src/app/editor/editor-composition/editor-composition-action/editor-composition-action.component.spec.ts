import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditorCompositionActionComponent } from './editor-composition-action.component';

describe('EditorCompositionActionComponent', () => {
  let component: EditorCompositionActionComponent;
  let fixture: ComponentFixture<EditorCompositionActionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditorCompositionActionComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EditorCompositionActionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
