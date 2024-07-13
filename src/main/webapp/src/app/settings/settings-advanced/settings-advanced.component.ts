import { InfoDialogService } from "./../../services/info-dialog.service";
import { WaitDialogService } from "./../../services/wait-dialog.service";
import { StateService } from "./../../services/state.service";
import { Component, OnInit, OnDestroy } from "@angular/core";
import { SettingsService } from "../../services/settings.service";
import { Settings } from "../../models/settings";
import { WarningDialogService } from "../../services/warning-dialog.service";
import { State } from "../../models/state";
import { saveAs } from "file-saver/FileSaver";
import { HttpClient } from "@angular/common/http";
import { OperatingSystemInformation } from "../../models/operating-system-information";
import { OperatingSystemInformationService } from "../../services/operating-system-information.service";
import { map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { SessionService } from "../../services/session.service";
import { ReloadClearCacheService } from "../../services/reload-clear-cache.service";

@Component({
  selector: "app-settings-advanced",
  templateUrl: "./settings-advanced.component.html",
  styleUrls: ["./settings-advanced.component.scss"],
})
export class SettingsAdvancedComponent implements OnInit, OnDestroy {
  private settingsChangedSubscription: Subscription;
  private stateChangedSubscription: Subscription;

  settings: Settings;
  private isResettingToFactory: boolean = false;
  operatingSystemInformation: OperatingSystemInformation;

  loggingLevelList: string[] = [];

  constructor(
    private settingsService: SettingsService,
    private sessionService: SessionService,
    private warningDialogService: WarningDialogService,
    private waitDialogService: WaitDialogService,
    private http: HttpClient,
    private stateService: StateService,
    private infoDialogService: InfoDialogService,
    private operatingSystemInformationService: OperatingSystemInformationService,
    private reloadClearCacheService: ReloadClearCacheService
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

    this.stateChangedSubscription = this.stateService.state.subscribe(
      (state: State) => {
        if (this.isResettingToFactory) {
          // We got a new state after resetting to factory defaults
          // -> the device has been resetted
          this.isResettingToFactory = false;

          this.infoDialogService
            .show("settings.factory-reset-done")
            .pipe(
              map(() => {
                this.reloadClearCacheService.reload();
              })
            )
            .subscribe();
        }
      }
    );
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
    this.stateChangedSubscription.unsubscribe();
  }

  factoryReset() {
    this.warningDialogService
      .show("settings.warning-factory-reset")
      .pipe(
        map((result) => {
          if (result) {
            this.waitDialogService.show("settings.wait-factory-reset");
            this.isResettingToFactory = true;
            this.http.post("system/factory-reset", undefined).subscribe();
          }
        })
      )
      .subscribe();
  }

  resetIntro() {
    this.sessionService.introReset().subscribe(() => {
      this.reloadClearCacheService.reload();
    });
  }

  private downloadFile(blob: Blob) {
    saveAs(blob, "logs.zip");
  }

  downloadLogs() {
    this.http
      .get("system/download-logs", { responseType: "blob" })
      .subscribe((blob) => {
        this.downloadFile(blob);
      });
  }
}
