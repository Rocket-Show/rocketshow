import { Component, Input } from "@angular/core";
import { ActionTriggerMidiProgramChange } from "../../../../models/action-trigger-midi-program-change";

@Component({
    selector: "app-action-trigger-midi-program-change",
    templateUrl: "./action-trigger-midi-program-change.component.html",
    styleUrl: "./action-trigger-midi-program-change.component.scss",
    standalone: false
})
export class ActionTriggerMidiProgramChangeComponent {
  @Input()
  trigger: ActionTriggerMidiProgramChange;

  programList: number[] = [];

  constructor() {
    for (let i = 0; i < 128; i++) {
      this.programList.push(i);
    }
  }
}
