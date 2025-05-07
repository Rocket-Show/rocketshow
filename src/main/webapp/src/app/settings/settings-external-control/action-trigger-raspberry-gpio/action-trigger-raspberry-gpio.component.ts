import { Component, Input } from "@angular/core";
import { ActionTriggerRaspberryGpio } from "../../../models/action-trigger-raspberry-gpio";

@Component({
  selector: "app-action-trigger-raspberry-gpio",
  templateUrl: "./action-trigger-raspberry-gpio.component.html",
  styleUrl: "./action-trigger-raspberry-gpio.component.scss",
})
export class ActionTriggerRaspberryGpioComponent {
  @Input()
  trigger: ActionTriggerRaspberryGpio;

  pinIdList: number[] = [];

  constructor() {
    this.pinIdList.push(4);
    this.pinIdList.push(5);
    this.pinIdList.push(6);
    this.pinIdList.push(7);
    this.pinIdList.push(8);
    this.pinIdList.push(9);
    this.pinIdList.push(10);
    this.pinIdList.push(11);
    this.pinIdList.push(12);
    this.pinIdList.push(13);
    this.pinIdList.push(14);
    this.pinIdList.push(15);
    this.pinIdList.push(16);
    this.pinIdList.push(17);
    this.pinIdList.push(18);
    this.pinIdList.push(19);
    this.pinIdList.push(20);
    this.pinIdList.push(21);
    this.pinIdList.push(22);
    this.pinIdList.push(23);
    this.pinIdList.push(24);
    this.pinIdList.push(25);
    this.pinIdList.push(26);
    this.pinIdList.push(27);
  }
}
