import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { AccountService } from '../../services/account.service';
import { AccountStatus } from '../../models/account-status';

// Links the device to an account of the online designer. Applied at once, not with the other settings.
@Component({
  selector: 'app-settings-account',
  templateUrl: './settings-account.component.html',
  styleUrl: './settings-account.component.scss',
  standalone: false
})
export class SettingsAccountComponent implements OnInit {

  status: AccountStatus;
  email = '';
  password = '';
  busy = false;

  // The translation key of what went wrong with the last login
  error: string;

  constructor(private accountService: AccountService) { }

  ngOnInit() {
    this.accountService.getStatus().subscribe((status) => {
      this.status = status;
    });
  }

  // What the device answered (or rocketshow.net through it), as a text for the user
  private getErrorKey(error: HttpErrorResponse): string {
    const code = error?.error?.error;

    if (code === 'wrong-credentials' || code === 'missing-parameters') {
      return 'settings.account.wrong-credentials';
    }

    if (code === 'api-unreachable' || code === 'api-timeout') {
      return 'settings.account.unreachable';
    }

    return 'settings.account.error';
  }

  login() {
    this.busy = true;
    this.error = undefined;

    this.accountService.login(this.email, this.password).pipe(
      finalize(() => {
        this.busy = false;
      })
    ).subscribe({
      next: (status) => {
        this.status = status;
        this.password = '';
      },
      error: (error) => {
        this.error = this.getErrorKey(error);
      }
    });
  }

  logout() {
    this.busy = true;
    this.error = undefined;

    this.accountService.logout().pipe(
      finalize(() => {
        this.busy = false;
      })
    ).subscribe((status) => {
      this.status = status;
    });
  }

}
