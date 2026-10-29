import { Component, Input } from "@angular/core";
import { ActionTriggerMidiControlChange } from "../../../../models/action-trigger-midi-control-change";

@Component({
    selector: "app-action-trigger-midi-control-change",
    templateUrl: "./action-trigger-midi-control-change.component.html",
    styleUrl: "./action-trigger-midi-control-change.component.scss",
    standalone: false
})
export class ActionTriggerMidiControlChangeComponent {
  @Input()
  trigger: ActionTriggerMidiControlChange;

  controllerList: number[] = [];

  constructor() {
    for (let i = 0; i < 128; i++) {
      this.controllerList.push(i);
    }
  }
}
