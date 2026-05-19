import { forkJoin } from "rxjs";
import { OlaPlugin } from "../../models/ola-plugin";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { Component, OnInit } from "@angular/core";
import { finalize, map, switchMap } from "rxjs/operators";
import { AuthService } from "../../services/auth.service";
import { DeviceInformationService } from "../../services/device-information.service";
import { DeviceInformation } from "../../models/device-information";
import { OlaPort } from "../../models/ola-port";
import { LightingUniverseMapping } from "../../models/lighting-universe-mapping";

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
  olaOutputPortList: OlaPort[] = [];
  selectedOlaPlugin: OlaPlugin;
  loading: boolean = true;
  savingOlaPlugins: boolean = false;
  savingLightingUniverses: boolean = false;
  lightingUniverseSortableOptions = {
    onUpdate: () => this.updateLightingUniverseMappingList(),
  };

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

          forkJoin({
            olaPlugins: this.settingsService.getOlaPluginList(),
            olaOutputPorts: this.settingsService.getOlaOutputPortList(),
          }).subscribe((response) => {
            this.allOlaPluginList = response.olaPlugins;
            this.olaOutputPortList = response.olaOutputPorts;
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
    this.savingOlaPlugins = true;
    this.settingsService
      .updateLightingOlaPlugins(this.settings.lightingOlaPluginList)
      .pipe(
        switchMap(() => this.settingsService.getOlaOutputPortList()),
        finalize(() => {
          this.savingOlaPlugins = false;
        })
      )
      .subscribe((olaOutputPorts) => {
        this.olaOutputPortList = olaOutputPorts;
      });
  }

  updateLightingUniverseMappingList() {
    this.savingLightingUniverses = true;
    this.settingsService
      .updateLightingUniverses(this.settings.lightingUniverseMappingList)
      .pipe(
        switchMap(() =>
          forkJoin({
            settings: this.settingsService.getSettings(true),
            olaOutputPorts: this.settingsService.getOlaOutputPortList(),
          })
        ),
        finalize(() => {
          this.savingLightingUniverses = false;
        })
      )
      .subscribe((response) => {
        this.settings = response.settings;
        this.olaOutputPortList = response.olaOutputPorts;
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

  addLightingUniverseMapping() {
    let lightingUniverseMapping = new LightingUniverseMapping();
    lightingUniverseMapping.name =
      "Universe " + (this.settings.lightingUniverseMappingList.length + 1);
    lightingUniverseMapping.olaUniverseId =
      this.getNextOlaUniverseId();

    this.settings.lightingUniverseMappingList.push(lightingUniverseMapping);
    this.updateLightingUniverseMappingList();
  }

  removeLightingUniverseMapping(index: number) {
    this.settings.lightingUniverseMappingList.splice(index, 1);
    this.updateLightingUniverseMappingList();
  }

  updateLightingUniverseMappingPort(lightingUniverseMapping: LightingUniverseMapping, olaOutputPortId: string) {
    lightingUniverseMapping.olaOutputPortId = olaOutputPortId || "";
    this.updateLightingUniverseMappingList();
  }

  private getNextOlaUniverseId(): number {
    let highestUniverseId = 0;

    for (let lightingUniverseMapping of this.settings.lightingUniverseMappingList) {
      if (lightingUniverseMapping.olaUniverseId > highestUniverseId) {
        highestUniverseId = lightingUniverseMapping.olaUniverseId;
      }
    }

    return highestUniverseId + 1;
  }

  getOlaOutputPortsForMapping(lightingUniverseMapping: LightingUniverseMapping): OlaPort[] {
    const selectedOlaOutputPortId = lightingUniverseMapping.olaOutputPortId || "";
    let olaOutputPorts = this.olaOutputPortList.filter((olaOutputPort) => {
      if (!olaOutputPort.id) {
        return false;
      }

      return (
        olaOutputPort.id === selectedOlaOutputPortId ||
        !this.isOlaOutputPortSelectedByOtherMapping(olaOutputPort.id, lightingUniverseMapping)
      );
    });

    if (
      selectedOlaOutputPortId &&
      !olaOutputPorts.some((olaOutputPort) => olaOutputPort.id === selectedOlaOutputPortId)
    ) {
      olaOutputPorts = [
        new OlaPort({
          id: selectedOlaOutputPortId,
        }),
        ...olaOutputPorts,
      ];
    }

    return olaOutputPorts;
  }

  private isOlaOutputPortSelectedByOtherMapping(
    olaOutputPortId: string,
    currentLightingUniverseMapping: LightingUniverseMapping
  ): boolean {
    if (!olaOutputPortId) {
      return false;
    }

    return this.settings.lightingUniverseMappingList.some(
      (lightingUniverseMapping) =>
        lightingUniverseMapping !== currentLightingUniverseMapping &&
        (lightingUniverseMapping.olaOutputPortId || "") === olaOutputPortId
    );
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
