import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewApiKeyDialogComponent } from './new-api-key-dialog.component';

describe('NewApiKeyDialogComponent', () => {
  let component: NewApiKeyDialogComponent;
  let fixture: ComponentFixture<NewApiKeyDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewApiKeyDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NewApiKeyDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
