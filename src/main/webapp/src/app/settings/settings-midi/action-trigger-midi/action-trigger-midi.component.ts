import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ActionTriggerMidi } from "../../../models/action-trigger-midi";
import { ActionTriggerMidiNoteOn } from "../../../models/action-trigger-midi-note-on";
import { ActionTriggerMidiProgramChange } from "../../../models/action-trigger-midi-program-change";
import { Settings } from "../../../models/settings";

@Component({
  selector: "app-action-trigger-midi",
  templateUrl: "./action-trigger-midi.component.html",
  styleUrl: "./action-trigger-midi.component.scss",
})
export class ActionTriggerMidiComponent {
  @Input()
  trigger: ActionTriggerMidi;

  @Input()
  index: number;

  @Output()
  change = new EventEmitter<{ index: number; newTrigger: ActionTriggerMidi }>();

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

  onTriggerMidiTypeChange(newValue: string): void {
    if (newValue === this.getTriggerMidiType()) {
      return;
    }

    // stringify and parse, take the first wrapped trigger, to keep the data (e.g. actions)
    let oldTrigger = JSON.parse(JSON.stringify(this.trigger));
    oldTrigger = oldTrigger[Object.keys(oldTrigger)[0]];

    if (newValue === "NOTE_ON") {
      this.trigger = new ActionTriggerMidiNoteOn(oldTrigger);
    } else if (newValue === "PROGRAM_CHANGE") {
      this.trigger = new ActionTriggerMidiProgramChange(oldTrigger);
    }

    this.change.emit({ index: this.index, newTrigger: this.trigger });
  }
}
