import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ActionTrigger } from "../models/action-trigger";
import { ActionTriggerMidi } from "../models/action-trigger-midi";
import { ActionTriggerComposition } from "../models/action-trigger-composition";
import { ActionTriggerRaspberryGpio } from "../models/action-trigger-raspberry-gpio";

@Component({
    selector: "app-action-trigger",
    templateUrl: "./action-trigger.component.html",
    styleUrl: "./action-trigger.component.scss",
    standalone: false
})
export class ActionTriggerComponent {
  @Input()
  trigger: ActionTrigger;

  @Input()
  index: number;

  @Input()
  listHandle: boolean = true;

  @Output()
  delete = new EventEmitter<number>();

  @Output()
  triggerChange = new EventEmitter<{ index: number; newTrigger: ActionTrigger }>();

  getTriggerType(): string {
    if (this.trigger instanceof ActionTriggerMidi) {
      return "MIDI";
    } else if (this.trigger instanceof ActionTriggerComposition) {
      return "COMPOSITION";
    } else if (this.trigger instanceof ActionTriggerRaspberryGpio) {
      return "RASPBERRY_GPIO";
    }
    return "UNKNOWN";
  }

  onTriggerChange(event: any) {
    this.triggerChange.emit(event);
  }
}
