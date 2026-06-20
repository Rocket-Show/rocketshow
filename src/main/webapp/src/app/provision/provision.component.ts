import { Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BsModalService } from 'ngx-bootstrap/modal';
import { DeviceInformation } from '../models/device-information';
import { HealthStatus } from '../models/health-status';
import { DeviceInformationService } from '../services/device-information.service';
import { HealthService } from '../services/health.service';
import { SettingsService } from '../services/settings.service';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';
import { UpdateDialogComponent } from '../settings/update-dialog/update-dialog.component';

@Component({
  selector: 'app-provision',
  templateUrl: './provision.component.html',
  styleUrls: ['./provision.component.scss'],
  standalone: false
})
export class ProvisionComponent implements OnInit {

  language: string = 'en';

  deviceInformation: DeviceInformation;
  provisioningData: DeviceInformation = new DeviceInformation();
  provisioningSaving: boolean = false;
  provisioningSaved: boolean = false;

  health: HealthStatus;
  healthLoading: boolean = false;

  constructor(
    public settingsService: SettingsService,
    private translateService: TranslateService,
    private deviceInformationService: DeviceInformationService,
    private healthService: HealthService,
    private modalService: BsModalService,
    private toastGeneralErrorService: ToastGeneralErrorService
  ) { }

  ngOnInit() {
    this.loadDeviceInformation();
    this.refreshHealth();
  }

  switchLanguage(language: string) {
    this.language = language;
    this.translateService.use(language);
  }

  private loadDeviceInformation() {
    this.deviceInformationService.getDeviceInformation().subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });
  }

  // Provisioning data is considered set once it is available and a serial has been stored.
  get provisioningDataSet(): boolean {
    return !!(this.deviceInformation?.available && this.deviceInformation?.serial);
  }

  storeProvisioningData() {
    this.provisioningSaving = true;

    this.deviceInformationService.storeDeviceInformation(this.provisioningData).subscribe({
      next: () => {
        this.provisioningSaving = false;
        this.provisioningSaved = true;
        this.loadDeviceInformation();
        // Reflect the new provisioning data in the health status as well.
        this.refreshHealth();
      },
      error: (err) => {
        this.provisioningSaving = false;
        this.toastGeneralErrorService.show(err);
      }
    });
  }

  refreshHealth() {
    this.healthLoading = true;

    this.healthService.getHealth().subscribe({
      next: (health) => {
        this.health = health;
        this.healthLoading = false;
      },
      error: (err) => {
        this.healthLoading = false;
        this.toastGeneralErrorService.show(err);
      }
    });
  }

  openUpdateDialog() {
    this.modalService.show(UpdateDialogComponent, {
      keyboard: false,
      ignoreBackdropClick: true,
      class: '',
    });
  }

}
