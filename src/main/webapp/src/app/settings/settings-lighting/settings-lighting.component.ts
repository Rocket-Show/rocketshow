import { Observable } from "rxjs";
import { OlaPlugin } from "../../models/ola-plugin";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { Component, OnInit } from "@angular/core";
import { map } from "rxjs/operators";
import { WaitDialogService } from "../../services/wait-dialog.service";

@Component({
  selector: "app-settings-lighting",
  templateUrl: "./settings-lighting.component.html",
  styleUrls: ["./settings-lighting.component.scss"],
})
export class SettingsLightingComponent implements OnInit {
  settings: Settings;
  private allOlaPluginList: OlaPlugin[] = [];
  availableOlaPluginList: OlaPlugin[] = [];
  selectedOlaPlugin: OlaPlugin;

  constructor(
    private settingsService: SettingsService,
    private waitDialogService: WaitDialogService
  ) {}

  private loadSettings() {
    this.waitDialogService.show();

    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.settings = result;

          this.settingsService.getOlaPluginList().subscribe((response) => {
            this.allOlaPluginList = response;
            this.refreshAvailableOlaPlugins();
            this.waitDialogService.hide();
          });
        })
      )
      .subscribe();
  }

  private refreshAvailableOlaPlugins() {
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
  }

  private updateOlaPluginList() {
    this.waitDialogService.show();
    this.settingsService
      .updateLightingOlaPlugins(this.settings.lightingOlaPluginList)
      .subscribe(() => {
        this.waitDialogService.hide();
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
