import { Component, Input } from "@angular/core";
import { ActionRaspberryGpio } from "../../models/action-raspberry-gpio";

@Component({
  selector: "app-action-raspberry-gpio",
  templateUrl: "./action-raspberry-gpio.component.html",
  styleUrl: "./action-raspberry-gpio.component.scss",
})
export class ActionRaspberryGpioComponent {
  @Input()
  action: ActionRaspberryGpio;
}
