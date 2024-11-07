import { OlaPlugin } from "../../models/ola-plugin";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { Component, OnInit } from "@angular/core";
import { map } from "rxjs/operators";

@Component({
  selector: "app-settings-lighting",
  templateUrl: "./settings-lighting.component.html",
  styleUrls: ["./settings-lighting.component.scss"],
})
export class SettingsLightingComponent implements OnInit {
  settings: Settings;
  olaPluginList: OlaPlugin[];

  constructor(private settingsService: SettingsService) {}

  private loadSettings() {
    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.settings = result;

          this.settingsService.getOlaPluginList().subscribe((response) => {
            this.olaPluginList = response;
          });
        })
      )
      .subscribe();
  }

  ngOnInit() {
    this.loadSettings();
  }
}
