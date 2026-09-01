import { Component, OnDestroy, OnInit } from "@angular/core";
import { Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { ScheduledComposition } from "../../models/scheduled-composition";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { UuidService } from "../../services/uuid.service";

@Component({
    selector: "app-settings-scheduler",
    templateUrl: "./settings-scheduler.component.html",
    styleUrl: "./settings-scheduler.component.scss",
    standalone: false
})
export class SettingsSchedulerComponent implements OnInit, OnDestroy {
  private settingsChangedSubscription: Subscription;

  settings: Settings;

  constructor(
    private settingsService: SettingsService,
    private uuidService: UuidService
  ) {}

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
  }

  addScheduledComposition() {
    let scheduledComposition = new ScheduledComposition();
    scheduledComposition.uuid = this.uuidService.getUuid();
    this.settings.scheduledCompositionList.push(scheduledComposition);
  }

  deleteScheduledComposition(scheduledCompositionIndex: number) {
    this.settings.scheduledCompositionList.splice(scheduledCompositionIndex, 1);
  }
}
