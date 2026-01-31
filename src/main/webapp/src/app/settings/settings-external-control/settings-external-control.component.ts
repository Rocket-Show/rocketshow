import { Component } from "@angular/core";
import { Settings } from "../../models/settings";
import { SettingsService } from "../../services/settings.service";
import { Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { ActionTriggerRaspberryGpio } from "../../models/action-trigger-raspberry-gpio";

@Component({
    selector: "app-settings-external-control",
    templateUrl: "./settings-external-control.component.html",
    styleUrl: "./settings-external-control.component.scss",
    standalone: false
})
export class SettingsExternalControlComponent {
  private settingsChangedSubscription: Subscription;

  settings: Settings;

  constructor(private settingsService: SettingsService) {}

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

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

  addActionTrigger() {
    let trigger = new ActionTriggerRaspberryGpio();
    this.settings.actionTriggerRaspberryGpioList.push(trigger);
  }

  deleteActionTrigger(triggerIndex: number) {
    this.settings.actionTriggerRaspberryGpioList.splice(triggerIndex, 1);
  }
}
