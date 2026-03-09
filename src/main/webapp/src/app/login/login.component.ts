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
  loading: boolean = false;

  constructor(
    public authService: AuthService
  ) {

  }

  change() {
    this.pwWrong = false;
  }

  login() {
    this.loading = true;
    this.authService.login(this.password).subscribe({
      next: () => {
        this.authService.init().subscribe();
        this.loading = false;
        this.password = null;
        this.pwWrong = false;
      },
      error: (err) => {
        this.loading = false;
        this.pwWrong = true;
      }
    });
  }

}
