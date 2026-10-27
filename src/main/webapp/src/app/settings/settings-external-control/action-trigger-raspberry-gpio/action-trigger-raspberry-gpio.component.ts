import { Component, Input } from "@angular/core";
import { ActionTriggerRaspberryGpio } from "../../../models/action-trigger-raspberry-gpio";
import { SettingsService } from "../../../services/settings.service";
import { RaspberryPiPin } from "../../../models/raspberry-pi-pin";
import { DeviceInformationService } from "../../../services/device-information.service";

@Component({
  selector: "app-action-trigger-raspberry-gpio",
  templateUrl: "./action-trigger-raspberry-gpio.component.html",
  styleUrl: "./action-trigger-raspberry-gpio.component.scss",
  standalone: false
})
export class ActionTriggerRaspberryGpioComponent {
  @Input()
  trigger: ActionTriggerRaspberryGpio;

  pinList: RaspberryPiPin[] = [];

  // SPARK offers its inputs by number, not by BCM pin
  spark: boolean;

  constructor(private settingsService: SettingsService, private deviceInformationService: DeviceInformationService) {
    this.spark = this.deviceInformationService.deviceInformation.available;

    if (this.spark) {
      // ready to use version
      this.pinList.push(new RaspberryPiPin(23, "1"));
      this.pinList.push(new RaspberryPiPin(25, "2"));
    } else {
      this.pinList = this.settingsService.raspberryPiPinIdList;
    }
  }
}
