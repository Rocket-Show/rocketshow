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

  constructor(
    public authService: AuthService
  ) {

  }

  change() {
    this.pwWrong = false;
  }

  login() {
    this.authService.login(this.password).subscribe({
      next: () => {
        this.authService.init().subscribe();
      },
      error: (err) => {
        this.pwWrong = true;
      }
    });
  }

}
