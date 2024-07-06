import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BackupRestoreDialogComponent } from './backup-restore-dialog.component';

describe('BackupRestoreDialogComponent', () => {
  let component: BackupRestoreDialogComponent;
  let fixture: ComponentFixture<BackupRestoreDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BackupRestoreDialogComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(BackupRestoreDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
