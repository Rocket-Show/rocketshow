import { Observable } from "rxjs";
import { OlaPlugin } from "../../models/ola-plugin";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { Component, OnInit } from "@angular/core";
import { map } from "rxjs/operators";
import { WaitDialogService } from "../../services/wait-dialog.service";
import { AuthService } from "../../services/auth.service";
import { DeviceInformationService } from "../../services/device-information.service";
import { DeviceInformation } from "../../models/device-information";

@Component({
  selector: "app-settings-lighting",
  templateUrl: "./settings-lighting.component.html",
  styleUrls: ["./settings-lighting.component.scss"],
  standalone: false
})
export class SettingsLightingComponent implements OnInit {
  settings: Settings;
  deviceInformation: DeviceInformation;
  private allOlaPluginList: OlaPlugin[] = [];
  availableOlaPluginList: OlaPlugin[] = [];
  selectedOlaPlugin: OlaPlugin;
  loading: boolean = true;

  constructor(
    private settingsService: SettingsService,
    private deviceInformationService: DeviceInformationService,
    private authService: AuthService,
  ) {
    this.deviceInformationService.getDeviceInformation().subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });
  }

  private loadSettings() {
    if (!this.authService.currentState?.authenticated) {
      return;
    }

    this.loading = true;

    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.settings = result;

          this.settingsService.getOlaPluginList().subscribe((response) => {
            this.allOlaPluginList = response;
            this.refreshAvailableOlaPlugins();
            this.loading = false;
          });
        })
      )
      .subscribe();
  }

  private refreshAvailableOlaPlugins() {
    if (!this.authService.currentState?.authenticated) {
      return;
    }

    this.availableOlaPluginList = [];
    this.selectedOlaPlugin = null;

    for (let olaPlugin of this.allOlaPluginList) {
      let alreadyActivated = false;
      for (let activatedOlaPlugin of this.settings.lightingOlaPluginList) {
        if (activatedOlaPlugin.name === olaPlugin.name) {
          alreadyActivated = true;
          break;
        }
      }
      if (!alreadyActivated) {
        this.availableOlaPluginList.push(olaPlugin);
      }
    }
    if (this.availableOlaPluginList.length > 0) {
      this.selectedOlaPlugin = this.availableOlaPluginList[0];
    }
  }

  ngOnInit() {
    this.loadSettings();

    this.authService.state.subscribe((state) => {
      if (state.authenticated) {
        this.loadSettings();
      }
    });
  }

  private updateOlaPluginList() {
    this.loading = true;
    this.settingsService
      .updateLightingOlaPlugins(this.settings.lightingOlaPluginList)
      .subscribe(() => {
        this.loading = false;
      });
  }

  removeOlaPlugin(olaPlugin: OlaPlugin) {
    for (var i = this.settings.lightingOlaPluginList.length - 1; i >= 0; i--) {
      if (this.settings.lightingOlaPluginList[i].name == olaPlugin.name) {
        this.settings.lightingOlaPluginList.splice(i, 1);
      }
    }
    this.updateOlaPluginList();
    this.refreshAvailableOlaPlugins();
  }

  addOlaPlugin() {
    if (!this.selectedOlaPlugin) {
      return;
    }
    this.settings.lightingOlaPluginList.push(this.selectedOlaPlugin);
    this.updateOlaPluginList();
    this.refreshAvailableOlaPlugins();
  }

  getConflictingPlugins(olaPlugin: OlaPlugin) {
    // only report activated plugins
    let conflictingPlugins: OlaPlugin[] = [];
    for (let pluginConflict of olaPlugin.conflictList) {
      if (
        this.settings.lightingOlaPluginList.some(
          (plugin) => plugin.name === pluginConflict.name
        )
      ) {
        conflictingPlugins.push(pluginConflict);
      }
    }
    return conflictingPlugins;
  }
}
