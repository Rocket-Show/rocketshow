import { Component, Input } from "@angular/core";
import { ActionTriggerMidi } from "../../../models/action-trigger-midi";
import { ActionTriggerMidiNoteOn } from "../../../models/action-trigger-midi-note-on";
import { ActionTriggerMidiProgramChange } from "../../../models/action-trigger-midi-program-change";

@Component({
  selector: "app-action-trigger-midi",
  templateUrl: "./action-trigger-midi.component.html",
  styleUrl: "./action-trigger-midi.component.scss",
})
export class ActionTriggerMidiComponent {
  @Input()
  trigger: ActionTriggerMidi;

  channelList: number[] = [];

  constructor() {
    for (let i = 0; i < 16; i++) {
      this.channelList.push(i);
    }
  }

  getTriggerMidiType(): string {
    if (this.trigger instanceof ActionTriggerMidiNoteOn) {
      return "NOTE_ON";
    } else if (this.trigger instanceof ActionTriggerMidiProgramChange) {
      return "PROGRAM_CHANGE";
    }
    return "UNKNOWN";
  }
}
