import { Component } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-change-password-dialog',
  standalone: false,
  templateUrl: './change-password-dialog.component.html',
  styleUrl: './change-password-dialog.component.scss',
})
export class ChangePasswordDialogComponent {

  loading: boolean = false;
  oldPw: string;
  newPw: string;
  newPwRepeat: string;
  error: boolean = false;

  constructor(
    private bsModalRef: BsModalRef,
    private authService: AuthService,
  ) {

  }

  public ok(): void {
    this.loading = true;
    this.error = false;
    this.authService.changePassword(this.oldPw, this.newPw).subscribe({
      next: () => {
        this.loading = false;
        this.bsModalRef.hide();
      },
      error: (err) => {
        this.loading = false;
        this.error = true;
      }
    });
  }

  public cancel(): void {
    this.bsModalRef.hide();
  }

}
