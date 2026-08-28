import { Component, Input } from "@angular/core";
import { ActionRaspberryGpio } from "../../models/action-raspberry-gpio";
import { UuidService } from "../../services/uuid.service";
import { SettingsService } from "../../services/settings.service";
import { RaspberryPiPin } from "../../models/raspberry-pi-pin";
import { DeviceInformationService } from "../../services/device-information.service";

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
    private settingsService: SettingsService,
    private deviceInformationService: DeviceInformationService,
  ) {
    this.uuid = this.uuidService.getUuid();

    if (this.deviceInformationService.deviceInformation.available) {
      // Ready to use version
      this.pinList.push(new RaspberryPiPin(24, "1"));
      this.pinList.push(new RaspberryPiPin(5, "2"));
      this.pinList.push(new RaspberryPiPin(27, "3"));
      this.pinList.push(new RaspberryPiPin(22, "4"));
    } else {
      this.pinList = this.settingsService.raspberryPiPinIdList;
    }
  }
}
