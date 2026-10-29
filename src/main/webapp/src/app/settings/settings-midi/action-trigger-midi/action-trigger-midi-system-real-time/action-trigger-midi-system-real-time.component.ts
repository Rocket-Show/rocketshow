import { Component, Input } from "@angular/core";
import { ActionTriggerMidiSystemRealTime } from "../../../../models/action-trigger-midi-system-real-time";

@Component({
    selector: "app-action-trigger-midi-system-real-time",
    templateUrl: "./action-trigger-midi-system-real-time.component.html",
    styleUrl: "./action-trigger-midi-system-real-time.component.scss",
    standalone: false
})
export class ActionTriggerMidiSystemRealTimeComponent {
  @Input()
  trigger: ActionTriggerMidiSystemRealTime;

  systemRealTimeTypes = ["START", "CONTINUE", "STOP"];
}
