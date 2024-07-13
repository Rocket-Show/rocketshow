import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DropzoneComponent } from './dropzone.component';

describe('DropzoneComponent', () => {
  let component: DropzoneComponent;
  let fixture: ComponentFixture<DropzoneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DropzoneComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DropzoneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
