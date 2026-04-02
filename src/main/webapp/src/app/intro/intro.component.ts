import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { SettingsService } from '../services/settings.service';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';
import { DeviceInformationService } from '../services/device-information.service';
import { DeviceInformation } from '../models/device-information';

@Component({
  selector: 'app-intro',
  templateUrl: './intro.component.html',
  styleUrls: ['./intro.component.scss'],
  standalone: false
})
export class IntroComponent {

  deviceInformation: DeviceInformation;

  currentStep = 1;
  maxStep = 1;
  language: string = 'en';
  deviceName: string = '';
  adminPassword1: string = '';
  adminPassword2: string = '';
  passwordsDontMatch: boolean = false;

  wifiApEnabled: boolean = true;
  wifiApPasswordSame: boolean = true;
  wifiApPassword1: string = '';
  wifiApPassword2: string = '';
  wifiApPasswordsDontMatch: boolean = false;

  tlsEnable: boolean = false;

  loading: boolean = false;

  constructor(
    public settingsService: SettingsService,
    private translateService: TranslateService,
    private authService: AuthService,
    private router: Router,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private deviceInformationService: DeviceInformationService
  ) {
    this.deviceInformationService.getDeviceInformation().subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });
  }

  switchLanguage(language: string) {
    this.language = language;
    this.translateService.use(language);
  }

  validatePassword() {
    this.passwordsDontMatch = false;

    if (this.adminPassword1 === this.adminPassword2) {
      return;
    }

    this.passwordsDontMatch = true;
  }

  nextStepAdminPw() {
    this.validatePassword();
    if (this.passwordsDontMatch) {
      return;
    }
    this.nextStep();
  }

  validateWifiApPassword() {
    this.wifiApPasswordsDontMatch = false;

    if (this.wifiApPassword1 === this.wifiApPassword2) {
      return;
    }

    this.wifiApPasswordsDontMatch = true;
  }

  nextStepWifiApPw() {
    this.validateWifiApPassword();
    if (this.wifiApPasswordsDontMatch) {
      return;
    }
    this.nextStep();
  }

  finish() {
    this.loading = true;

    this.translateService.get('intro.default-unit-name').subscribe((result) => {
      if (!this.deviceName) {
        this.deviceName = result;
      }

      this.authService.setup(
        this.language,
        this.deviceName,
        this.adminPassword1,
        this.wifiApEnabled,
        this.wifiApPasswordSame ? this.adminPassword1 : this.wifiApPassword1,
        this.tlsEnable
      ).subscribe({
        next: () => {
          this.authService.init().subscribe(() => {
            this.settingsService.settingsChanged.next();

            const host = window.location.hostname;
            const targetUrl = this.tlsEnable
              ? `https://${host}`
              : `http://${host}`;

            window.location.replace(targetUrl);
          });
        },
        error: (err) => {
          this.toastGeneralErrorService.show(err);
          this.loading = false;
        }
      });
    });
  }

  nextStep(): void {
    if (this.currentStep < 5) {
      this.currentStep++;
      this.maxStep = this.currentStep;
    }
  }

  prevStep(): void {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  goToStep(step: number): void {
    if (step >= 1 && step <= 5 && step <= this.maxStep) {
      this.currentStep = step;
    }
  }

}