import { Component } from '@angular/core';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  standalone: false
})
export class LoginComponent {

  password: string;
  pwWrong: boolean = false;
  generalError: boolean = false;
  generalErrorMessage: string = '';
  loading: boolean = false;

  constructor(
    public authService: AuthService
  ) {

  }

  change() {
    this.pwWrong = false;
    this.generalError = false;
  }

  login() {
    this.loading = true;
    this.pwWrong = false;
    this.generalError = false;
    this.authService.login(this.password).subscribe({
      next: () => {
        this.authService.init().subscribe();
        this.loading = false;
        this.password = null;
        this.pwWrong = false;
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 401) {
          this.pwWrong = true;
        } else {
          this.generalError = true;
          this.generalErrorMessage = err.statusText;
        }
        console.log(err)
      }
    });
  }

}
