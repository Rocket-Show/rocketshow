import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ActionTriggerMidi } from "../../../models/action-trigger-midi";
import { ActionTriggerMidiNoteOn } from "../../../models/action-trigger-midi-note-on";
import { ActionTriggerMidiProgramChange } from "../../../models/action-trigger-midi-program-change";
import { ActionTriggerMidiControlChange } from "../../../models/action-trigger-midi-control-change";
import { ActionTriggerMidiSongSelect } from "../../../models/action-trigger-midi-song-select";
import { ActionTriggerMidiSystemRealTime } from "../../../models/action-trigger-midi-system-real-time";
import { Settings } from "../../../models/settings";

@Component({
    selector: "app-action-trigger-midi",
    templateUrl: "./action-trigger-midi.component.html",
    styleUrl: "./action-trigger-midi.component.scss",
    standalone: false
})
export class ActionTriggerMidiComponent {
  @Input()
  trigger: ActionTriggerMidi;

  @Input()
  index: number;

  @Output()
  triggerChange = new EventEmitter<{ index: number; newTrigger: ActionTriggerMidi }>();

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
    } else if (this.trigger instanceof ActionTriggerMidiControlChange) {
      return "CONTROL_CHANGE";
    } else if (this.trigger instanceof ActionTriggerMidiSongSelect) {
      return "SONG_SELECT";
    } else if (this.trigger instanceof ActionTriggerMidiSystemRealTime) {
      return "SYSTEM_REAL_TIME";
    }
    return "UNKNOWN";
  }

  // Song select and the transport messages are system messages, which carry no channel
  hasChannel(): boolean {
    const type = this.getTriggerMidiType();

    return type != "SONG_SELECT" && type != "SYSTEM_REAL_TIME";
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
    } else if (newValue === "CONTROL_CHANGE") {
      this.trigger = new ActionTriggerMidiControlChange(oldTrigger);
    } else if (newValue === "SONG_SELECT") {
      this.trigger = new ActionTriggerMidiSongSelect(oldTrigger);
    } else if (newValue === "SYSTEM_REAL_TIME") {
      this.trigger = new ActionTriggerMidiSystemRealTime(oldTrigger);
    }

    this.triggerChange.emit({ index: this.index, newTrigger: this.trigger });
  }
}
