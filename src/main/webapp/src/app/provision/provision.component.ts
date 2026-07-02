import { Component, OnInit } from '@angular/core';
import { BsModalService } from 'ngx-bootstrap/modal';
import { DeviceInformation } from '../models/device-information';
import { HealthStatus } from '../models/health-status';
import { AuthService } from '../services/auth.service';
import { DeviceInformationService } from '../services/device-information.service';
import { HealthService } from '../services/health.service';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';
import { UpdateDialogComponent } from '../settings/update-dialog/update-dialog.component';

@Component({
  selector: 'app-provision',
  templateUrl: './provision.component.html',
  styleUrls: ['./provision.component.scss'],
  standalone: false
})
export class ProvisionComponent implements OnInit {

  deviceInformation: DeviceInformation;
  provisioningData: DeviceInformation = new DeviceInformation();
  provisioningSaving: boolean = false;
  provisioningSaved: boolean = false;

  health: HealthStatus;
  healthLoading: boolean = false;

  constructor(
    private deviceInformationService: DeviceInformationService,
    private healthService: HealthService,
    private modalService: BsModalService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private authService: AuthService
  ) { }

  // Updating is only possible while the device is still unprovisioned (no admin
  // password set yet) or when an admin is logged in. Otherwise the backend
  // rejects the update with an authorization error, so the button is disabled.
  get canUpdate(): boolean {
    const state = this.authService.currentState;
    return !!state && (!state.passwordConfigured || state.authenticated);
  }

  ngOnInit() {
    this.loadDeviceInformation();
    this.refreshHealth();
  }

  private loadDeviceInformation() {
    // Always fetch fresh, so a device provisioned in a previous session (and
    // possibly cached as "not provisioned") is reflected correctly.
    this.deviceInformationService.getDeviceInformation(true).subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });
  }

  // Provisioning data is considered set once a serial has been stored.
  get provisioningDataSet(): boolean {
    return !!this.deviceInformation?.serial;
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
