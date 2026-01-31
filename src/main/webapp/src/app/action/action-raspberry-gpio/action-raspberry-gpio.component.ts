import { Component, Input } from "@angular/core";
import { ActionRaspberryGpio } from "../../models/action-raspberry-gpio";
import { UuidService } from "../../services/uuid.service";
import { SettingsService } from "../../services/settings.service";
import { RaspberryPiPin } from "../../models/raspberry-pi-pin";

@Component({
    selector: "app-action-raspberry-gpio",
    templateUrl: "./action-raspberry-gpio.component.html",
    styleUrl: "./action-raspberry-gpio.component.scss",
    standalone: false
})
export class ActionRaspberryGpioComponent {
  @Input()
  action: ActionRaspberryGpio;

  uuid: string;
  pinList: RaspberryPiPin[] = [];

  constructor(
    private uuidService: UuidService,
    private settingsService: SettingsService
  ) {
    this.uuid = this.uuidService.getUuid();

    if (this.settingsService.settings.readyToUseVersion) {
      this.pinList.push(new RaspberryPiPin(5, "1"));
      this.pinList.push(new RaspberryPiPin(25, "2"));
    } else {
      this.pinList = this.settingsService.raspberryPiPinIdList;
    }
  }
}
