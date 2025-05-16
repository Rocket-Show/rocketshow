import { Component, Input } from "@angular/core";
import { ActionTriggerRaspberryGpio } from "../../../models/action-trigger-raspberry-gpio";
import { SettingsService } from "../../../services/settings.service";
import { RaspberryPiPin } from "../../../models/raspberry-pi-pin";

@Component({
  selector: "app-action-trigger-raspberry-gpio",
  templateUrl: "./action-trigger-raspberry-gpio.component.html",
  styleUrl: "./action-trigger-raspberry-gpio.component.scss",
})
export class ActionTriggerRaspberryGpioComponent {
  @Input()
  trigger: ActionTriggerRaspberryGpio;

  pinList: RaspberryPiPin[] = [];

  constructor(private settingsService: SettingsService) {
    if (this.settingsService.settings.readyToUseVersion) {
      this.pinList.push(new RaspberryPiPin(27, "1"));
      this.pinList.push(new RaspberryPiPin(22, "2"));
      this.pinList.push(new RaspberryPiPin(23, "3"));
      this.pinList.push(new RaspberryPiPin(24, "4"));
    } else {
      this.pinList = this.settingsService.raspberryPiPinIdList;
    }
  }
}
