import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ActionTrigger } from "../models/action-trigger";
import { ActionTriggerMidi } from "../models/action-trigger-midi";

@Component({
  selector: "app-action-trigger",
  templateUrl: "./action-trigger.component.html",
  styleUrl: "./action-trigger.component.scss",
})
export class ActionTriggerComponent {
  @Input()
  trigger: ActionTrigger;

  @Input()
  index: number;

  @Output()
  delete = new EventEmitter<number>();

  getTriggerType(): string {
    if (this.trigger instanceof ActionTriggerMidi) {
      return "MIDI";
    }
    // else if (this.trigger instanceof ActionTriggerCompo) {
    //   return "COMPOSITION";
    // } else if (this.trigger instanceof ActionTriggerRaspberryGpio) {
    //   return "RASPBERRY_GPIO";
    // }
    return "UNKNOWN";
  }
}
