import { InfoDialogService } from "./../../services/info-dialog.service";
import { WaitDialogService } from "./../../services/wait-dialog.service";
import { Component, OnInit, OnDestroy } from "@angular/core";
import { SettingsService } from "../../services/settings.service";
import { Settings } from "../../models/settings";
import { WarningDialogService } from "../../services/warning-dialog.service";
import { saveAs } from "file-saver/FileSaver";
import { HttpClient } from "@angular/common/http";
import { OperatingSystemInformation } from "../../models/operating-system-information";
import { OperatingSystemInformationService } from "../../services/operating-system-information.service";
import { finalize, map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { AuthService } from "../../services/auth.service";

@Component({
    selector: "app-settings-advanced",
    templateUrl: "./settings-advanced.component.html",
    styleUrls: ["./settings-advanced.component.scss"],
    standalone: false
})
export class SettingsAdvancedComponent implements OnInit, OnDestroy {
  private settingsChangedSubscription: Subscription;
  private factoryResetAuthPollSubscription: Subscription;

  settings: Settings;
  enableMaintenanceModeLoading: boolean = false;
  downloadLogsLoading: boolean = false;
  operatingSystemInformation: OperatingSystemInformation;

  loggingLevelList: string[] = [];

  constructor(
    private settingsService: SettingsService,
    private warningDialogService: WarningDialogService,
    private waitDialogService: WaitDialogService,
    private http: HttpClient,
    private infoDialogService: InfoDialogService,
    private operatingSystemInformationService: OperatingSystemInformationService,
    private authService: AuthService
  ) {
    this.loggingLevelList.push("INFO");
    this.loggingLevelList.push("DEBUG");
    this.loggingLevelList.push("TRACE");

    this.operatingSystemInformationService
      .getOperatingSystemInformation()
      .subscribe((operatingSystemInformation) => {
        this.operatingSystemInformation = operatingSystemInformation;
      });
  }

  private loadSettings() {
    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.settings = result;
        })
      )
      .subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription =
      this.settingsService.settingsChanged.subscribe(() => {
        this.loadSettings();
      });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
    this.factoryResetAuthPollSubscription?.unsubscribe();
  }

  factoryReset() {
    this.warningDialogService
      .show("settings.warning-factory-reset")
      .pipe(
        map((result) => {
          if (result) {
            this.waitDialogService.show("settings.wait-factory-reset");
            this.http.post("system/factory-reset", undefined).subscribe({
              next: () => {
                this.reloadWhenFactoryResetFinished();
              },
              error: () => {
                this.reloadWhenFactoryResetFinished();
              },
            });
          }
        })
      )
      .subscribe();
  }

  private reloadWhenFactoryResetFinished(): void {
    this.factoryResetAuthPollSubscription?.unsubscribe();
    this.factoryResetAuthPollSubscription = this.authService
      .pollForStateAfterConnectionLoss()
      .subscribe(() => {
        window.location.replace(window.location.origin + "/");
      });
  }

  private downloadFile(blob: Blob) {
    saveAs(blob, "logs.zip");
  }

  downloadLogs() {
    if (this.downloadLogsLoading) {
      return;
    }

    this.downloadLogsLoading = true;

    this.http
      .get("system/download-logs", { responseType: "blob" })
      .pipe(
        finalize(() => {
          this.downloadLogsLoading = false;
        })
      )
      .subscribe((blob) => {
        this.downloadFile(blob);
      });
  }

  enableMaintenanceMode() {
    if (this.enableMaintenanceModeLoading) {
      return;
    }

    this.enableMaintenanceModeLoading = true;

    this.http
      .post("system/enable-ssh", undefined, { responseType: "text" })
      .pipe(
        finalize(() => {
          this.enableMaintenanceModeLoading = false;
        })
      )
      .subscribe((password) => {
        this.infoDialogService
          .show("settings.maintenance-mode-enabled", { password: password })
          .subscribe();
      });
  }
}
