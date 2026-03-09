import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-settings-security',
  templateUrl: './settings-security.component.html',
  styleUrl: './settings-security.component.scss',
  standalone: false
})
export class SettingsSecurityComponent {

  constructor(
    public authService: AuthService,
  ) {

  }

  public logout() {
    this.authService.logout().subscribe({
      next: () => {
        this.authService.init().subscribe();
      },
      error: (err) => { }
    });
  }

}
