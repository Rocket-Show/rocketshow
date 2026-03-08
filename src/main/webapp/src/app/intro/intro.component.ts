import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { SettingsService } from '../services/settings.service';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';

@Component({
  selector: 'app-intro',
  templateUrl: './intro.component.html',
  styleUrls: ['./intro.component.scss'],
  standalone: false
})
export class IntroComponent {

  @ViewChild('deviceNameInput') deviceNameInput!: ElementRef<HTMLInputElement>;
  @ViewChild('carousel') carousel!: ElementRef<HTMLInputElement>;

  language: string = 'en';
  deviceName: string = '';
  adminPassword1: string = '';
  adminPassword2: string = '';

  passwordsDontMatch: boolean = false;
  loading: boolean = false;

  constructor(
    public settingsService: SettingsService,
    private translateService: TranslateService,
    private authService: AuthService,
    private router: Router,
    private toastGeneralErrorService: ToastGeneralErrorService
  ) { }

  switchLanguage(language: string) {
    this.language = language;
    this.translateService.use(language);
  }

  validate() {
    if (this.adminPassword1 === this.adminPassword2) {
      this.passwordsDontMatch = false;
    }
  }

  finish() {
    if (this.adminPassword1 != this.adminPassword2) {
      this.passwordsDontMatch = true;
      return;
    }

    this.loading = true;
    this.translateService.get('intro.default-unit-name').subscribe((result) => {
      if (!this.deviceName) {
        this.deviceName = result;
      }

      this.authService.setup(this.language, this.deviceName, this.adminPassword1).subscribe({
        next: () => {
          this.authService.init().subscribe(() => {
            this.settingsService.settingsChanged.next();

            this.router.navigate(['/play']);
          });
        },
        error: (err) => {
          this.toastGeneralErrorService.show(err);
          this.loading = false;
        }
      });
    });
  }

}